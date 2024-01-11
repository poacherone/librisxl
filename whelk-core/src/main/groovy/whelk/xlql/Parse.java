package whelk.xlql;

import java.util.*;
import java.util.function.Function;

public class Parse
{
    /**
     * LALR(1) EBNF
     * (ORCOMB is the root node)
     *
     * ORCOMB: ANDCOMB ( "OR" ANDCOMB )*
     * GROUP: "(" ORCOMB | ANDCOMB | GROUP ")"
     * ANDCOMB: TERM ( "AND" TERM | TERM )*
     * TERM: STRING | GROUP | UOPERATOR TERM | UOPERATOR GROUP
     * UOPERATOR: "!" | "~" | "NOT" | CODE
     * CODE: [STRING ending in ":"]
     * STRING: ...
     */

    public static class ParseException extends Exception {
        public ParseException(String s) {
            super(s);
        }
    }

    public record Group(OrComb o, AndComb a, Group g) {}
    public record OrComb(List<AndComb> andCombs) {}
    public record AndComb(List<Term> ts) {}
    public record Term (String s, Uoperator uop, Term t, Group g) {}
    public record Uoperator (String s, String c) {}

    public static OrComb parseQuery(LinkedList<Lex.Symbol> symbols) throws ParseException {
        LinkedList<Object> stack = new LinkedList<>();
        while (!symbols.isEmpty()) {
            shift(stack, symbols);
            boolean reductionWasPossible;
            do {
                Lex.Symbol lookahead = null;
                if (!symbols.isEmpty())
                    lookahead = symbols.get(0);
                reductionWasPossible = reduce(stack, lookahead);


                System.out.println("After reduction, stack and next symbols:\n\tstack:");//\n\t stack: " + stack);
                for (Object o : stack) {
                    System.out.println("\t\t"+o.toString());
                }
                if (!symbols.isEmpty())
                    System.out.println("\t next: " + lookahead + "\n");
                else
                    System.out.println();


            }
            while(reductionWasPossible);
        }

        System.out.println("Parse termination.");
        if (symbols.isEmpty() && stack.size() == 1 && stack.get(0) instanceof OrComb) {
            return (OrComb) stack.get(0);
        }

        throw new ParseException("TODO");
    }

    // Note to self, the front of the list counts as the top!
    private static void shift(LinkedList<Object> stack, LinkedList<Lex.Symbol> symbols) {
        stack.push( symbols.poll() );
    }

    private static boolean reduce(LinkedList<Object> stack, Lex.Symbol lookahead) {

        // UOPERATOR: "!" | "~" | "NOT" | CODE
        {
            if (stack.size() >= 1) {
                if (stack.get(0) instanceof Lex.Symbol s &&
                        s.name() == Lex.TokenName.STRING &&
                        ( s.value().equals("!") || s.value().equals("~") ) ) {
                    stack.pop();
                    stack.push(new Uoperator(s.value(), null));
                    return true;
                }
                else if (stack.get(0) instanceof Lex.Symbol c && c.name() == Lex.TokenName.CODE) {
                    stack.pop();
                    stack.push(new Uoperator(null, c.value()));
                    return true;
                }
            }
        }

        // TERM: STRING | GROUP | UOPERATOR TERM | UOPERATOR GROUP
        {
            if (stack.size() >= 2) {
                if (stack.get(1) instanceof Uoperator uop) {
                    if (stack.get(0) instanceof Term t) {
                        stack.pop();
                        stack.pop();
                        stack.push(new Term(null, uop, t, null));
                        return true;
                    }
                    if (stack.get(0) instanceof Group g) {
                        stack.pop();
                        stack.pop();
                        stack.push(new Term(null, uop, null, g));
                        return true;
                    }
                }
            }
            if (stack.size() >= 1) {
                if (stack.get(0) instanceof Lex.Symbol s &&
                        s.name() == Lex.TokenName.STRING) {
                    stack.pop();
                    stack.push(new Term(s.value(), null, null, null));
                    return true;
                }
                if (stack.get(0) instanceof Group g) {
                    stack.pop();
                    stack.push(new Term(null, null, null, g));
                    return true;
                }
            }
        }

        // ANDCOMB: TERM ( "AND" TERM | TERM )*
        {
            if (stack.size() >= 1) {
                if (stack.get(0) instanceof Term t) {

                    // This is where the 1 in LALR(1) comes in.
                    // We must check that we have everything that goes into the list, *on*
                    // the stack before reducing. In other words, our lookahead must be
                    // something that cannot be part of the list (or EOF) before we reduce.

                    boolean wholeListOnStack = true; // Assumption
                    if (lookahead != null) {
                        if (lookahead.name() == Lex.TokenName.STRING)
                            wholeListOnStack = false;
                        if (lookahead.name() == Lex.TokenName.CODE)
                            wholeListOnStack = false;
                        if (lookahead.name() == Lex.TokenName.OPERATOR && lookahead.value().equals("!"))
                            wholeListOnStack = false;
                        if (lookahead.name() == Lex.TokenName.OPERATOR && lookahead.value().equals("~"))
                            wholeListOnStack = false;
                        if (lookahead.name() == Lex.TokenName.KEYWORD && lookahead.value().equals("not"))
                            wholeListOnStack = false;
                        if (lookahead.name() == Lex.TokenName.KEYWORD && lookahead.value().equals("and"))
                            wholeListOnStack = false;
                    }

                    if (wholeListOnStack) {
                        List<Term> terms = new ArrayList<>();
                        terms.add(t);
                        stack.pop();

                        // Chew the whole list all at once
                        boolean stillChewing;
                        do {
                            stillChewing = false;
                            if (!stack.isEmpty() && stack.get(0) instanceof Term nextTerm) {
                                stack.pop();
                                terms.add(nextTerm);
                                stillChewing = true;
                            } else if (stack.size() >= 2 && stack.get(0) instanceof Lex.Symbol s &&
                                    s.name() == Lex.TokenName.KEYWORD &&
                                    s.value().equals("and") &&
                                    stack.get(1) instanceof Term nextTerm) {
                                stack.pop();
                                stack.pop();
                                terms.add(nextTerm);
                                stillChewing = true;
                            }
                        } while (stillChewing);

                        stack.push(new AndComb(terms));
                        return true;
                    }
                }
            }
        }

        // ORCOMB: ANDCOMB ( "OR" ANDCOMB )*
        {
            if (stack.size() >= 1) {
                if (stack.get(0) instanceof AndComb ac) {

                    boolean wholeListOnStack = true; // Assumption
                    if (lookahead != null) {
                        if (lookahead.name() == Lex.TokenName.KEYWORD && lookahead.value().equals("or"))
                            wholeListOnStack = false;
                    }

                    if (wholeListOnStack) {
                        List<AndComb> ACs = new ArrayList<>();
                        ACs.add(ac);
                        stack.pop();

                        // Chew the whole list all at once
                        boolean stillChewing;
                        do {
                            stillChewing = false;
                            if (stack.size() >= 2 && stack.get(0) instanceof Lex.Symbol s &&
                                    s.name() == Lex.TokenName.KEYWORD &&
                                    s.value().equals("or") &&
                                    stack.get(1) instanceof AndComb nextAc) {
                                stack.pop();
                                stack.pop();
                                ACs.add(nextAc);
                                stillChewing = true;
                            }
                        } while (stillChewing);

                        stack.push(new OrComb(ACs));
                        return true;
                    }
                }
            }
        }

        // GROUP: "(" ORCOMB | ANDCOMB | GROUP ")"
        {
            if (stack.size() >= 3) {
                if (stack.get(0) instanceof Lex.Symbol s1 &&
                        s1.name() == Lex.TokenName.OPERATOR &&
                        s1.value().equals(")") &&
                        stack.get(2) instanceof Lex.Symbol s2 &&
                        s2.name() == Lex.TokenName.OPERATOR &&
                        s2.value().equals("(")) {

                    if (stack.get(1) instanceof OrComb oc) {
                        stack.pop();
                        stack.pop();
                        stack.pop();
                        stack.push(new Group(oc, null, null));
                        return true;
                    }
                    if (stack.get(1) instanceof AndComb ac) {
                        stack.pop();
                        stack.pop();
                        stack.pop();
                        stack.push(new Group(null, ac, null));
                        return true;
                    }
                    if (stack.get(1) instanceof Group g) {
                        stack.pop();
                        stack.pop();
                        stack.pop();
                        stack.push(new Group(null, null, g));
                        return true;
                    }

                }
            }
        }

        return false;
    }
}