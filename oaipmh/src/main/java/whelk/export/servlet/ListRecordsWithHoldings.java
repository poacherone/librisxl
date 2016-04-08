package whelk.export.servlet;

import org.codehaus.jackson.map.ObjectMapper;
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
import java.util.HashMap;

public class ListRecordsWithHoldings {

    /**
     * Sends a response to a ListRecords (or ListIdentifiers) request, with a metadataPrefix tagged with _includehold.
     * Each bib record in the response will come with an about section with info on all holdings for that bib post.
     */
    public static void respond(HttpServletRequest request, HttpServletResponse response,
                               ZonedDateTime fromDateTime, ZonedDateTime untilDateTime, SetSpec setSpec,
                               String metadataPrefix, boolean onlyIdentifiers)
            throws IOException, XMLStreamException, SQLException
    {
        try (Connection dbconn = DataBase.getConnection();
             PreparedStatement preparedStatement = Helpers.getMatchingDocumentsStatement(dbconn, fromDateTime, untilDateTime, setSpec);
             ResultSet resultSet = preparedStatement.executeQuery())
        {
            // Is the resultset empty?
            if (!resultSet.isBeforeFirst())
            {
                ResponseCommon.sendOaiPmhError(OaiPmh.OAIPMH_ERROR_NO_RECORDS_MATCH, "", request, response);
                return;
            }

            // Build the xml response feed
            XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
            xmlOutputFactory.setProperty("escapeCharacters", false); // Inline xml must be left untouched.
            XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(response.getOutputStream());

            ResponseCommon.writeOaiPmhHeader(writer, request, true);

            if (onlyIdentifiers)
                writer.writeStartElement("ListIdentifiers");
            else
                writer.writeStartElement("ListRecords");

            while (resultSet.next())
            {
                emitRecord(resultSet, writer, metadataPrefix, onlyIdentifiers);
            }

            writer.writeEndElement(); // ListIdentifiers/ListRecords
            ResponseCommon.writeOaiPmhClose(writer, request);
        }
    }

    /*
    private static void emitRecordHeader(ResultSet resultSet, XMLStreamWriter writer, boolean onlyIdentifiers)
            throws SQLException, XMLStreamException, IOException
    {
    }*/

    private static void emitRecord(ResultSet resultSet, XMLStreamWriter writer, String requestedFormat, boolean onlyIdentifiers)
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
        //ZonedDateTime modified = ZonedDateTime.ofInstant(resultSet.getTimestamp("modified").toInstant(), ZoneOffset.UTC);
        //writer.writeCharacters(modified.toString());
        writer.writeCharacters(jsonLDdoc.getModified().toString());
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

        if (!onlyIdentifiers)
            writer.writeEndElement(); // record
    }
}
