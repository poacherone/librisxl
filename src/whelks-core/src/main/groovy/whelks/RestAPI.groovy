package se.kb.libris.conch

import org.restlet.*
import org.restlet.data.*
import org.restlet.resource.*

import se.kb.libris.whelks.Document
import se.kb.libris.conch.*
import se.kb.libris.conch.component.*

class RestAPI extends Restlet {  

    def whelk


    RestAPI(def whelk) {
        this.whelk = whelk
    }

    def void handle(Request request, Response response) {  
        if (request.method == Method.GET) {
            def query = request.getResourceRef().getQueryAsForm().getValuesMap()
            if (query.containsKey("load")) {
                def d = whelk.retrieve(query.get("load"))
                println "Loaded something from whelk : $d"
                response.setEntity(new String(d.data), MediaType.APPLICATION_JSON)
            }
            else {
                response.setEntity("Hello groovy!", MediaType.TEXT_PLAIN)
            }
        } 
        else if (request.method == Method.PUT) {
            Form form = request.getResourceRef().getQueryAsForm()
            def filename = form.getValues('filename')
            def type = form.getValues('type')
            def upload = request.entityAsText
            println "Expecting upload of file $filename of type $type"
            println "upload: ${upload}"
            def doc = new MyDocument(filename).withData(upload.getBytes('UTF-8'))
            doc.type = type
            doc.index = whelk.name
            def identifier = whelk.ingest(doc)
            response.setEntity("Thank you! Document ingested with id ${identifier}", MediaType.TEXT_PLAIN)
        }
        else {
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Unhandled request method")
        }
    }

    static main(args) {
        Whelk w = new Whelk("groovywhelk")

        w.components.add(new DiskStorage())
        w.components.add(new ElasticSearchNodeIndex())

        RestAPI api = new RestAPI(w)
        //
        // Create the HTTP server and listen on port 8182  
        new Server(Protocol.HTTP, 8182, api).start()
    }
}  
