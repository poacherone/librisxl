package whelk.rest.api

import whelk.Document
import whelk.Whelk
import whelk.component.PostgreSQLComponent
import whelk.util.*

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RecordRelationAPI extends HttpServlet {

    private Whelk whelk

    @Override
    void init() {
        Properties configuration = PropertyLoader.loadProperties("secret")
        PostgreSQLComponent storage = new PostgreSQLComponent(configuration.getProperty("sqlUrl"),
                configuration.getProperty("sqlMaintable"))
        whelk = new Whelk(storage)
    }

    @Override
    void doGet(HttpServletRequest request, HttpServletResponse response) {
        String id = request.getParameter("id")
        String relation = request.getParameter("relation")
        String reverseString = request.getParameter("reverse")

        boolean reverse = false
        if (reverseString != null && reverseString.equals("true"))
            reverse = true

        if (id == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "\"id\" parameter required.")
            return
        }

        id = LegacyIntegrationTools.fixUri(id)
        String systemId = whelk.storage.getSystemIdByIri(id)
        if (systemId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "The supplied \"id\"-parameter must refer to an existing bibliographic record.")
            return
        }

        List<String> dependencySystemIDs
        if (relation == null) {
            if (reverse)
                dependencySystemIDs = whelk.storage.getDependers(systemId)
            else
                dependencySystemIDs = whelk.storage.getDependencies(systemId)
        }
        else {
            if (reverse)
                dependencySystemIDs = whelk.storage.getDependersOfType(systemId, relation)
            else
                dependencySystemIDs = whelk.storage.getDependenciesOfType(systemId, relation)
        }

        ArrayList<String> result = []
        for (String dependencySystemId : dependencySystemIDs){
            result.add(Document.getBASE_URI().toString() + dependencySystemId)
        }

        String jsonString = PostgreSQLComponent.mapper.writeValueAsString(result)
        response.setContentType("application/json")
        OutputStream out = response.getOutputStream()
        out.write(jsonString.getBytes("UTF-8"))
        out.close()
    }
}