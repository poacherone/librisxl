package whelk.housekeeping;

import org.apache.jena.atlas.logging.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import whelk.Document;
import whelk.Whelk;
import whelk.component.PostgreSQLComponent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static whelk.util.Jackson.mapper;

public class ImageLinker extends HouseKeeper {
    private final String IMAGES_STATE_KEY = "linkedNewImagesUpTo";
    private final String INSTANCES_STATE_KEY = "linkedNewInstancesUpTo";
    private String status = "OK";
    private final Whelk whelk;
    private final Logger logger = LogManager.getLogger(this.getClass());

    public ImageLinker(Whelk whelk) {
        this.whelk = whelk;
    }

    public String getName() {
        return "Image linker";
    }

    public String getStatusDescription() {
        return status;
    }

    public String getCronSchedule() {
        return "* * * * *";
    }

    public void trigger() {
        scanForNewImages();
        scanForNewInstances();
    }

    public void scanForNewInstances() {
        Timestamp linkNewInstancesSince = Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS));
        Map linkerState = whelk.getStorage().getState(getName());
        if (linkerState != null && linkerState.containsKey(INSTANCES_STATE_KEY))
            linkNewInstancesSince = Timestamp.from( ZonedDateTime.parse( (String) linkerState.get(INSTANCES_STATE_KEY), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() );
        Instant linkedNewInstancesUpTo = linkNewInstancesSince.toInstant();

        String newImagesSql = """
                SELECT
                  data#>>'{@graph,1,@id}' as instanceUri, data#>>'{@graph,1,identifiedBy}' as identifiedBy, created
                FROM
                  lddb
                WHERE
                  collection = 'bib' AND
                  deleted = false AND
                  created > ?;
                """.stripIndent();

        try (Connection connection = whelk.getStorage().getOuterConnection();
             PreparedStatement statement = connection.prepareStatement(newImagesSql)) {

            statement.setTimestamp(1, linkNewInstancesSince);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Instant created = resultSet.getTimestamp("created").toInstant();
                    String instanceUri = resultSet.getString("instanceUri");
                    String identifiedByString = resultSet.getString("identifiedBy");

                    List identifiedByObject = mapper.readValue(identifiedByString, List.class);

                    List<String> imagesToLink = new ArrayList<>();
                    for (Object indirectID : identifiedByObject) {
                        if (indirectID instanceof Map identifiedByMap) {
                            if (identifiedByMap.get("@type").equals("ISBN")) {
                                List<String> uris = getImagesByISBN((String) identifiedByMap.get("value"));
                                imagesToLink.addAll(uris);
                            }
                        }
                    }

                    if (created.isAfter(linkedNewInstancesUpTo))
                        linkedNewInstancesUpTo = created;

                    for (String imageUri : imagesToLink) {
                        linkImage(instanceUri, imageUri);
                    }
                }
            }
        } catch (Throwable e) {
            StringWriter w = new StringWriter();
            PrintWriter p = new PrintWriter(w);
            e.printStackTrace(p);
            String stacktrace = w.toString();
            status = "Failed with:\n" + e + "\nat:\n" + stacktrace;
            throw new RuntimeException(e);
        }

        if (linkedNewInstancesUpTo.isAfter(linkNewInstancesSince.toInstant())) {
            Map<String, String> newState = new HashMap<>();
            newState.put(INSTANCES_STATE_KEY, linkedNewInstancesUpTo.atOffset(ZoneOffset.UTC).toString());
            whelk.getStorage().putState(getName(), newState);
        }
    }

    public void scanForNewImages() {

        Timestamp linkNewImagesSince = Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS));
        Map linkerState = whelk.getStorage().getState(getName());
        if (linkerState != null && linkerState.containsKey(IMAGES_STATE_KEY))
            linkNewImagesSince = Timestamp.from( ZonedDateTime.parse( (String) linkerState.get(IMAGES_STATE_KEY), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() );
        Instant linkedNewImagesUpTo = linkNewImagesSince.toInstant();

        String newImagesSql = """
                SELECT
                  data#>'{@graph,1,@id}' as imageUri, data#>>'{@graph,1,indirectlyIdentifiedBy}' as indirectlyIdentifiedBy, data#>>'{@graph,1,imageOf}' as imageOf, created
                FROM
                  lddb
                WHERE
                  collection = 'none' AND
                  data#>'{@graph,0,inDataset}' @> '[{"@id": "https://id.kb.se/dataset/images"}]'::jsonb AND
                  data#>>'{@graph,1,@type}' = 'ImageObject' AND
                  deleted = false AND
                  created > ?;
                """.stripIndent();

        try (Connection connection = whelk.getStorage().getOuterConnection();
             PreparedStatement statement = connection.prepareStatement(newImagesSql)) {

            statement.setTimestamp(1, linkNewImagesSince);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Instant created = resultSet.getTimestamp("created").toInstant();
                    String imageUri = resultSet.getString("imageUri");
                    String imageOfUriString = resultSet.getString("imageOf");
                    String indirectIDsString = resultSet.getString("indirectlyIdentifiedBy");

                    List imageOfUriObjects = mapper.readValue(imageOfUriString, List.class);
                    List indirectIDsObjects = mapper.readValue(indirectIDsString, List.class);

                    if (created.isAfter(linkedNewImagesUpTo))
                        linkedNewImagesUpTo = created;

                    List<String> instancesThatShouldLinkToImage = new ArrayList<>();
                    for (Object imageOf : imageOfUriObjects) {
                        if (imageOf instanceof Map imageOfMap) {
                            instancesThatShouldLinkToImage.add( (String) imageOfMap.get("@id") );
                        }
                    }
                    for (Object indirectID : indirectIDsObjects) {
                        if (indirectID instanceof Map indirectIDmap) {
                            if (indirectIDmap.get("@type").equals("ISBN")) {
                                List<String> uris = getInstancesByISBN((String) indirectIDmap.get("value"));
                                instancesThatShouldLinkToImage.addAll(uris);
                            }
                        }
                    }

                    for (String instanceUri : instancesThatShouldLinkToImage) {
                        linkImage(instanceUri, imageUri);
                    }
                }
            }
        } catch (Throwable e) {
            StringWriter w = new StringWriter();
            PrintWriter p = new PrintWriter(w);
            e.printStackTrace(p);
            String stacktrace = w.toString();
            status = "Failed with:\n" + e + "\nat:\n" + stacktrace;
            throw new RuntimeException(e);
        }

        if (linkedNewImagesUpTo.isAfter(linkNewImagesSince.toInstant())) {
            Map<String, String> newState = new HashMap<>();
            newState.put(IMAGES_STATE_KEY, linkedNewImagesUpTo.atOffset(ZoneOffset.UTC).toString());
            whelk.getStorage().putState(getName(), newState);
        }
    }

    private List<String> getInstancesByISBN(String isbn) {
        List<String> result = new ArrayList<>();

        String getByISBNsql = """
                SELECT
                  data#>>'{@graph,1,@id}' as uri
                FROM
                  lddb
                WHERE
                  deleted = false AND
                  collection = 'bib' AND
                  data#>'{@graph,1,identifiedBy}' @> ?
                """.stripIndent();

        try(PostgreSQLComponent.ConnectionContext ignored = new PostgreSQLComponent.ConnectionContext(whelk.getStorage().connectionContextTL)) {
            try (PreparedStatement statement = whelk.getStorage().getMyConnection().prepareStatement(getByISBNsql)) {
                statement.setObject(1, "[{\"@type\": \"ISBN\", \"value\": \"" + isbn + "\"}]", java.sql.Types.OTHER);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    result.add( resultSet.getString("uri") );
                }
            }
        } catch (Throwable e) {
            StringWriter w = new StringWriter();
            PrintWriter p = new PrintWriter(w);
            e.printStackTrace(p);
            String stacktrace = w.toString();
            status = "Failed with:\n" + e + "\nat:\n" + stacktrace;
            throw new RuntimeException(e);
        }

        return result;
    }

    private List<String> getImagesByISBN(String isbn) {
        List<String> result = new ArrayList<>();

        String getByISBNsql = """
                SELECT
                  data#>>'{@graph,1,@id}' as uri
                FROM
                  lddb
                WHERE
                  collection = 'none' AND
                  data#>'{@graph,0,inDataset}' @> '[{"@id": "https://id.kb.se/dataset/images"}]'::jsonb AND
                  data#>>'{@graph,1,@type}' = 'ImageObject' AND
                  deleted = false AND
                  data#>'{@graph,1,indirectlyIdentifiedBy}' @> ?
                """.stripIndent();

        try(PostgreSQLComponent.ConnectionContext ignored = new PostgreSQLComponent.ConnectionContext(whelk.getStorage().connectionContextTL)) {
            try (PreparedStatement statement = whelk.getStorage().getMyConnection().prepareStatement(getByISBNsql)) {
                statement.setObject(1, "[{\"@type\": \"ISBN\", \"value\": \"" + isbn + "\"}]", java.sql.Types.OTHER);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    result.add( resultSet.getString("uri") );
                }
            }
        } catch (Throwable e) {
            StringWriter w = new StringWriter();
            PrintWriter p = new PrintWriter(w);
            e.printStackTrace(p);
            String stacktrace = w.toString();
            status = "Failed with:\n" + e + "\nat:\n" + stacktrace;
            throw new RuntimeException(e);
        }

        return result;
    }

    private void linkImage(String instanceUri, String imageUri) {
        if (instanceUri.startsWith("/")) // A relative URI
            instanceUri = Document.getBASE_URI().resolve(instanceUri).toString();

        String instanceId = whelk.getStorage().getSystemIdByIri(instanceUri);

        whelk.storeAtomicUpdate(instanceId, true, false, "ImageLinker", "SEK",
                (Document doc) -> {
                    doc.addImage(imageUri);
                    doc.setGenerationDate(new Date());
                    doc.setGenerationProcess("http://id.kb.se/imagelinker");
                });
        logger.info("Linked " + instanceId + " to image " + imageUri);
    }
}
