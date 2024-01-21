package whelk.xlql

import spock.lang.Specification

class AstSpec extends Specification {

    def "normal tree"() {
        given:
        def input = "AAA BBB and (CCC or DDD)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        //System.err.println(ast)
        expect:
        ast == new Ast.And([new Ast.Or(["DDD", "CCC"]), "BBB", "AAA"])
    }

    def "normal query"() {
        given:
        def input = "subject: \"lcsh:Physics\" AND NOT published < 2023 AND \"svarta hål\""
        def lexedSymbols = Lex.lexQuery(input)
            Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        expect:
        ast == new Ast.And(
                [
                        "svarta hål",
                        new Ast.Not(new Ast.CodeLesserGreaterThan("published", "<", "2023")),
                        new Ast.CodeEquals("subject", "lcsh:Physics")
                ]
        )
    }

    def "normal query2"() {
        given:
        def input = "subject: (\"lcsh:Physics\" OR Fysik) AND NOT published < 2023"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        expect:
        ast == new Ast.And(
                [
                        new Ast.Not(new Ast.CodeLesserGreaterThan("published", "<", "2023")),
                        new Ast.CodeEquals("subject", new Ast.Or(["Fysik", "lcsh:Physics"]))
                ]
        )
    }

    def "codes and quotes"() {
        given:
        def input = "\"bf:subject\":\"lcsh:Physics\" AND \"bf:subject\""
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        
        expect:
        ast == new Ast.And(
                [
                        "bf:subject",
                        new Ast.CodeEquals("bf:subject", "lcsh:Physics")
                ]
        )
    }

    def "comparison"() {
        given:
        def input = "published >= 2000"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        expect:
        ast == new Ast.CodeLesserGreaterThan("published", ">=", "2000")
    }

    def "comparison2"() {
        given:
        def input = "Pippi author=\"Astrid Lindgren\" published<=1970"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        expect:
        ast == new Ast.And(
                [
                        new Ast.CodeLesserGreaterThan("published", "<=", "1970"),
                        new Ast.CodeEquals("author", "Astrid Lindgren"),
                        "Pippi"
                ]
        )
    }

    def "Fail code of code"() {
        given:
        def input = "AAA:(BBB:CCC)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)

        when:
        Ast.buildFrom(parseTree)
        then:
        thrown BadQueryException
    }

    def "Flatten code groups"() {
        given:
        def input = "AAA:(BBB and CCC)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenCodes(ast)

        expect:
        flattened == new Ast.And(
                [
                        new Ast.CodeEquals("AAA", "CCC"),
                        new Ast.CodeEquals("AAA", "BBB"),
                ]
        )
    }

    def "Flatten code groups2"() {
        given:
        def input = "author:(Alice and (Bob or Cecilia))"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenCodes(ast)

        expect:
        flattened == new Ast.And(
                [
                        new Ast.Or([
                                new Ast.CodeEquals("author", "Cecilia"),
                                new Ast.CodeEquals("author", "Bob"),
                        ]),
                        new Ast.CodeEquals("author", "Alice"),
                ]
        )
    }

    def "Flatten code groups3"() {
        given:
        def input = "author:(Alice and (Bob or Cecilia) and not David)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenCodes(ast)

        expect:
        flattened == new Ast.And(
                [
                        new Ast.Not(new Ast.CodeEquals("author", "David")),
                        new Ast.Or([
                                new Ast.CodeEquals("author", "Cecilia"),
                                new Ast.CodeEquals("author", "Bob"),
                        ]),
                        new Ast.CodeEquals("author", "Alice"),
                ]
        )
    }

    def "Flatten code groups4"() {
        given:
        def input = "\"everything\" or author:(Alice and (Bob or Cecilia) and not David)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenCodes(ast)

        expect:
        flattened == new Ast.Or(
                [
                        new Ast.And(
                        [
                                new Ast.Not(new Ast.CodeEquals("author", "David")),
                                new Ast.Or([
                                        new Ast.CodeEquals("author", "Cecilia"),
                                        new Ast.CodeEquals("author", "Bob"),
                                ]),
                                new Ast.CodeEquals("author", "Alice"),
                        ]),
                        "everything"
                ]
        )
    }

    def "flatten negations"() {
        def input = "\"everything\" and not (author:Alice and published > 2022)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenNegations(ast)

        expect:
        flattened == new Ast.And(
                [
                        new Ast.Or(
                                [
                                        new Ast.CodeLesserGreaterThan("published", "<=", "2022"),
                                        new Ast.NotCodeEquals("author", "Alice")
                                ]
                        ),
                        "everything"
                ]
        )
    }

    def "flatten negations 2"() {
        def input = "\"everything\" and !(author:Alice and not published: 2022)"
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)
        Object flattened = Analysis.flattenNegations(ast)

        expect:
        flattened == new Ast.And(
                [
                        new Ast.Or(
                                [
                                        new Ast.CodeEquals("published", "2022"),
                                        new Ast.NotCodeEquals("author", "Alice")
                                ]
                        ),
                        "everything"
                ]
        )
    }

    def "flatten negations 3: fail when trying to negate free text"() {
        def input = "author:Alice and not \"everything\""
        def lexedSymbols = Lex.lexQuery(input)
        Parse.OrComb parseTree = Parse.parseQuery(lexedSymbols)
        Object ast = Ast.buildFrom(parseTree)

        when:
        Analysis.flattenNegations(ast)
        then:
        thrown BadQueryException
    }
}
