package whelk.export.servlet;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import whelk.Document;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

public class ResponseCommon
{
    private static final Logger logger = LoggerFactory.getLogger(ResponseCommon.class);

    /**
     * Send a properly formatted OAI-PMH error response to the requesting harvester.
     */
    public static void sendOaiPmhError(String errorCode, String extraMessage, HttpServletRequest request, HttpServletResponse response)
            throws IOException, XMLStreamException
    {
        response.setContentType("text/xml");
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(response.getOutputStream());

        // The OAI-PMH specification requires that parameters be echoed in response, unless the response has an error
        // code of badVerb or badArgument, in which case the parameters must be omitted.
        boolean includeParameters = true;
        if (errorCode.equals(OaiPmh.OAIPMH_ERROR_BAD_VERB) || errorCode.equals(OaiPmh.OAIPMH_ERROR_BAD_ARGUMENT))
            includeParameters = false;

        writeOaiPmhHeader(writer, request, includeParameters);

        writer.writeStartElement("error");
        writer.writeAttribute("code", errorCode);
        writer.writeCharacters(extraMessage);
        writer.writeEndElement();

        writeOaiPmhClose(writer, request);
    }

    /**
     * Send an OAI-PMH error (and return true) if there are any more parameters than the expected ones in the request
     */
    public static boolean errorOnExtraParameters(HttpServletRequest request, HttpServletResponse response, String... expectedParameters)
            throws IOException, XMLStreamException
    {
        String unknownParameters = Helpers.getUnknownParameters(request, expectedParameters);
        if (unknownParameters != null)
        {
            sendOaiPmhError(OaiPmh.OAIPMH_ERROR_BAD_ARGUMENT,
                    "Request contained unknown parameter(s): " + unknownParameters, request, response);
            return true;
        }
        return false;
    }

    public static void writeOaiPmhHeader(XMLStreamWriter writer, HttpServletRequest request, boolean includeParameters)
            throws IOException, XMLStreamException
    {
        // Static header
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("OAI-PMH");
        writer.writeDefaultNamespace("http://www.openarchives.org/OAI/2.0/");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation",
                "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd");

        // Mandatory time element
        writer.writeStartElement("responseDate");
        writer.writeCharacters( ZonedDateTime.now(ZoneOffset.UTC).toString() );
        writer.writeEndElement();

        // Mandatory request element
        writer.writeStartElement("request");
        if (includeParameters)
        {
            Enumeration parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements())
            {
                String parameterName = (String) parameterNames.nextElement();
                String parameterValue = request.getParameter(parameterName);
                writer.writeAttribute(parameterName, parameterValue);
            }
        }
        writer.writeCharacters( request.getRequestURL().toString() );
        writer.writeEndElement();
    }

    public static void writeOaiPmhClose(XMLStreamWriter writer, HttpServletRequest req)
            throws IOException, XMLStreamException
    {
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
        logger.info("Response sent successfully to {}:{}.", req.getRemoteAddr(), req.getRemotePort());
    }

    public static void writeConvertedDocument(XMLStreamWriter writer, String formatPrefix, Document jsonLDdoc)
            throws IOException, XMLStreamException
    {
        OaiPmh.FormatDescription formatDescription = OaiPmh.supportedFormats.get(formatPrefix);

        // Convert if the format has a converter (otherwise assume jsonld)
        String convertedText = null;
        if (formatDescription.converter != null)
        {
            try
            {
                Document convertedDocument = formatDescription.converter.convert(jsonLDdoc);
                convertedText = (String) convertedDocument.getData().get("content");
            }
            catch (Exception e) // Depending on the converter, a variety of exceptions may been thrown here.
            {
                writer.writeCharacters("Error: Document conversion failed.");
                logger.error("Conversion failed for document: " + jsonLDdoc.getId(), e);
                return;
            }
        }
        else
            convertedText = jsonLDdoc.getDataAsString();

        // If the format is not XML, it needs to be embedded as CDATA, to not interfere with the response XML format.
        if (formatDescription.isXmlFormat)
            writer.writeCharacters(convertedText);
        else
            writer.writeCData(convertedText);
    }

    public static void emitRecord(ResultSet resultSet, XMLStreamWriter writer, String requestedFormat, boolean onlyIdentifiers)
            throws SQLException, XMLStreamException, IOException
    {
        ObjectMapper mapper = new ObjectMapper();

        String data = resultSet.getString("data");
        String manifest = resultSet.getString("manifest");
        boolean deleted = resultSet.getBoolean("deleted");
        String sigel = resultSet.getString("sigel");
        HashMap datamap = mapper.readValue(data, HashMap.class);
        HashMap manifestmap = mapper.readValue(manifest, HashMap.class);
        Document jsonLDdoc = new Document(datamap, manifestmap);

        if (!onlyIdentifiers)
            writer.writeStartElement("record");

        writer.writeStartElement("header");

        if (deleted)
            writer.writeAttribute("status", "deleted");

        writer.writeStartElement("identifier");
        writer.writeCharacters(jsonLDdoc.getURI().toString());
        writer.writeEndElement(); // identifier

        writer.writeStartElement("datestamp");
        ZonedDateTime modified = ZonedDateTime.ofInstant(resultSet.getTimestamp("modified").toInstant(), ZoneOffset.UTC);
        writer.writeCharacters(modified.toString());
        writer.writeEndElement(); // datestamp

        String dataset = (String) manifestmap.get("collection");
        if (dataset != null)
        {
            writer.writeStartElement("setSpec");
            writer.writeCharacters(dataset);
            writer.writeEndElement(); // setSpec
        }

        if (sigel != null)
        {
            writer.writeStartElement("setSpec");
            // Output sigel without quotation marks (").
            writer.writeCharacters(dataset + ":" + sigel.replace("\"", ""));
            writer.writeEndElement(); // setSpec
        }

        writer.writeEndElement(); // header

        if (!onlyIdentifiers && !deleted)
        {
            writer.writeStartElement("metadata");
            ResponseCommon.writeConvertedDocument(writer, requestedFormat, jsonLDdoc);
            writer.writeEndElement(); // metadata
        }

        if (requestedFormat.endsWith(OaiPmh.FORMAT_INCLUDE_HOLD_POSTFIX) && dataset.equals("bib"))
        {
            emitAttachedHoldings(jsonLDdoc.getItIdentifiers(), writer);
        }

        if (!onlyIdentifiers)
            writer.writeEndElement(); // record
    }

    private static void emitAttachedHoldings(List<String> itIds, XMLStreamWriter writer)
            throws SQLException, XMLStreamException, IOException
    {
        try (Connection dbconn = DataBase.getConnection();
             PreparedStatement preparedStatement = getAttachedHoldings(dbconn, itIds);
             ResultSet resultSet = preparedStatement.executeQuery())
        {
            // Is the resultset empty?
            if (!resultSet.isBeforeFirst())
                return;

            writer.writeStartElement("about");
            while(resultSet.next())
            {
                writer.writeStartElement("holding");
                writer.writeAttribute("sigel", resultSet.getString("sigel").replace("\"", ""));
                writer.writeAttribute("id", resultSet.getString("id"));
                writer.writeEndElement(); // holding
            }
            writer.writeEndElement(); // about
        }
    }

    private static PreparedStatement getAttachedHoldings(Connection dbconn, List<String> itIds)
            throws SQLException
    {
        String tableName = OaiPmh.configuration.getProperty("sqlMaintable");

        StringBuilder selectSQL = new StringBuilder("SELECT id, data#>'{@graph,1,heldBy,notation}' AS sigel FROM ");
        selectSQL.append(tableName);
        selectSQL.append(" WHERE manifest->>'collection' = 'hold' AND deleted = false AND (");

        for (int i = 0; i < itIds.size(); ++i)
        {
            selectSQL.append(" data#>>'{@graph,1,holdingFor,@id}' = ? ");

            // If this is the last id
            if (i+1 == itIds.size())
                selectSQL.append(")");
            else
                selectSQL.append("OR");
        }

        PreparedStatement preparedStatement = dbconn.prepareStatement(selectSQL.toString());

        for (int i = 0; i < itIds.size(); ++i)
        {
            preparedStatement.setString(i+1, itIds.get(i));
        }

        preparedStatement.setFetchSize(32);
        return preparedStatement;
    }
}
