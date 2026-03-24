package dk.itu.group12.bornholm;

import javafx.application.Application;
import parser.osmParser;

public class Main {
    public static void main(String[] args) {
        Application.launch(App.class, args); //Kør programmet - Ø'en der skal vises kan ændres i App.java
        // files can be reached like samso/samso.osm or bondeholm/bondeholm.osm osv.
        osmParser parser = new osmParser("samso/samso.osm");
        parser.parse();
        // get bounding box of osmParser with parser.getBoundingBox()
        System.out.println(parser.getBoundingBox());
    }
}