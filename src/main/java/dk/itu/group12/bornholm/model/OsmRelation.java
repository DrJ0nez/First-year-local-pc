package dk.itu.group12.bornholm.model;

import java.util.HashMap;
import java.util.List;

public class OsmRelation extends OsmElement {

    private List<Member> members;
    private HashMap<String, String> tags;

    public OsmRelation(long id, List<Member> members, HashMap<String, String> tags) {
        super(id);
        this.members = members;
        this.tags = tags;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    public HashMap<String, String> getTags() {
        return tags;
    }

    public void setTags(HashMap<String, String> tags) {
        this.tags = tags;
    }
}