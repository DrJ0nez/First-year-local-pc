package parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.*;

import dk.itu.group12.bornholm.Interfaces.IParser;
import dk.itu.group12.bornholm.model.*;

public class osmParser implements IParser {

    private final String fileName;
    private final List<Double> boundingBox = new ArrayList<>();
    private final HashMap<Long, OsmNode> osmNodeMap = new HashMap<>();
    private final HashMap<Long, OsmWay> osmWayMap = new HashMap<>();
    private final HashMap<Long, OsmRelation> osmRelationMap = new HashMap<>();
    private final HashMap<Long, HeightCurve> heightCurveMap = new HashMap<>();

    public osmParser(String filename) {
        this.fileName = filename;
    }

    @Override
    public void parse() {
        try {
            File compressedFile = new File("src/main/resources/hc-osm-files.zip");
            ZipFile zipFile = new ZipFile(compressedFile);
            /*
            zipFile.getEntry searches for a specific file path out from the zip files current path
            EXAMPLE: zipFile has the path (.../hc-osm-files.zip) so zipFile.getEntry() returns the path
            (.../hc-osm-files.zip/samso.osm)
             */

            InputStream is = zipFile.getInputStream(zipFile.getEntry("hc-osm-files/" + fileName));
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<bounds")) parseBounds(line);
                else if (line.startsWith("<node")) parseNodes(line);
                else if (line.startsWith("<way")) parseWay(line, br);
                else if (line.startsWith("<relation")) parseRelation (line, br);
                else if (line.startsWith("<hc") && !line.startsWith("<hcs")) parseHeightCurve(line, br);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private void parseRelation(String line, BufferedReader br) throws IOException {
        long id = getAttributeLong(line, "id");
        List<Member> members =  new ArrayList<>();
        HashMap<String, String> tags = new HashMap<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("</relation>")) break;

            if (line.startsWith("<member")) {
                String type = getAttribute(line, "type");
                long refId = getAttributeLong(line, "ref");
                String role = getAttribute(line, "role");

                OsmElement element = null;

                if (type.equals("node")) {
                    if (osmNodeMap.containsKey(refId)) {
                        element = osmNodeMap.get(refId);
                    } else continue; // ignores node if it doesn't appear in osm file
                }
                else if (type.equals("way")) {
                    if (osmWayMap.containsKey(refId)) {
                        element = osmWayMap.get(refId);
                    } else continue; // ignores way if it doesn't appear in osm file
                }
                else if (type.equals("relation")) {
                    if (osmRelationMap.containsKey(refId)) { // Update existing relation
                        OsmRelation relation = osmRelationMap.get(refId);
                        relation.setMembers(members);
                        relation.setTags(tags);
                    } else { // Create new placeholder for reference
                        OsmRelation relation = new OsmRelation(refId, new ArrayList<>(), new HashMap<>());
                        osmRelationMap.put(refId, relation);
                        element = relation;
                    }
                }
                members.add(new Member(element, role));

            } else if (line.contains("<tag")) {
                String k = getAttribute(line, "k");
                String v = getAttribute(line, "v");
                tags.put(k, v);
            }
        }
        if (osmRelationMap.containsKey(id)) {
            // if already exists, then update
            osmRelationMap.get(id).setMembers(members);
            osmRelationMap.get(id).setTags(tags);
        } else {
            // if never parsed then create new object
            OsmRelation relation = new OsmRelation(id, members, tags);
            osmRelationMap.put(id, relation);
        }
    }

    private void parseWay(String line, BufferedReader br) throws IOException {
        long id = getAttributeLong(line, "id");
        List<OsmNode> nodes = new ArrayList<>();
        HashMap<String, String> tags = new HashMap<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("</way>")) break;

            if (line.startsWith("<nd")) {
                long refId = getAttributeLong(line, "ref");
                OsmNode node = osmNodeMap.get(refId);
                if (node != null) nodes.add(node); //Tilføjet null tjek
            } else if (line.contains("<tag")) {
                String k = getAttribute(line, "k");
                String v = getAttribute(line, "v");
                tags.put(k, v);
            }
        }
        OsmWay way = new OsmWay(id, nodes, tags);
        osmWayMap.put(id, way);
    }

    private void parseNodes(String line) {
        long id = getAttributeLong(line, "id");
        double lat = getAttributeDouble(line, "lat");
        double lon = getAttributeDouble(line, "lon");
        OsmNode node = new OsmNode(id, lat, lon);
        osmNodeMap.put(id, node);
    }

    private void parseBounds(String line) {
        boundingBox.add(getAttributeDouble(line, "minlat"));
        boundingBox.add(getAttributeDouble(line, "minlon"));
        boundingBox.add(getAttributeDouble(line, "maxlat"));
        boundingBox.add(getAttributeDouble(line, "maxlon"));
    }


    private void parseHeightCurve(String line, BufferedReader br) throws IOException {
        long id = getAttributeLong(line, "id");
        double height = getAttributeDouble(line, "height");
        List<OsmNode> nodes = new ArrayList<>();
        List<Coordinate> coords = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if(line.startsWith("</hc")) break;

            if(line.startsWith("<coords")) {
                double lat = getAttributeDouble(line, "lat");
                double lon = getAttributeDouble(line, "lon");
                coords.add(new Coordinate(lat, lon));
            }

            if(line.startsWith("<nd")) {
                long refId = getAttributeLong(line, "ref");
                OsmNode node = osmNodeMap.get(refId);
                nodes.add(node);
            }
        }
        HeightCurve hc = new HeightCurve(id, height, coords);
        heightCurveMap.put(id, hc);
    }


    public String getAttribute(String s, String key) {
        String pattern = key + "=\"";
        int start = s.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        int valueStart = start + pattern.length();
        int valueEnd = s.indexOf('"', valueStart);
        return s.substring(valueStart, valueEnd);
    }

    public double getAttributeDouble(String s, String key) {
        String val = getAttribute(s, key);
        if (val == null) {
            return Double.NaN;
        }
        return Double.parseDouble(val);
    }

    public long getAttributeLong(String s, String key) {
        String val = getAttribute(s, key);
        if (val == null) {
            return 0L;
        }
        return Long.parseLong(val);
    }

    //DO NOT MODIFY BELOW GETTER METHODS
    @Override
    public List<Double> getBoundingBox() {
        return boundingBox;
    }

    @Override
    public HashMap<Long, OsmNode> getOsmNodeMap() {
        return osmNodeMap;
    }

    @Override
    public HashMap<Long, OsmWay> getOsmWayMap() {
        return osmWayMap;
    }

    @Override
    public HashMap<Long, OsmRelation> getOsmRelationMap() {
        return osmRelationMap;
    }
}
