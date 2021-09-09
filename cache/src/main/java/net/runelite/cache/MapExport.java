/*
Doogle Maps Tile Generator
Copyright (C) 2019  Weird Gloop

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Author: Ralph Bisschops <ralph.bisschops.dev@gmail.com>
*/

package net.runelite.cache;

import net.runelite.cache.definitions.loaders.WorldMapLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.RegionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.*;
import net.runelite.cache.region.Region;
import net.runelite.cache.definitions.WorldMapDefinition;

public class MapExport {

    private static final Logger logger = LoggerFactory.getLogger(MapExport.class);

    private RegionLoader regionLoader;


    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);

        // Path to Runescape case folder.
        // Make sure to update the Runescape client before running this script
        // NOTE: All paths are tested using Linux, on windows paths will be different.
        // Extra: this folder should contain 'main_file_cache.idx0' and similar file (about 25 files)
        //String cache = "C:/Users/Jimi/jagexcache/oldschool/LIVE/";
        String cache = "C:/Users/Jimi/jagexcache_2021-09-02/";
        //String cache = "C:/Users/Jimi/jagextestcache/";
        // Path to somethere on system where you want the map to export.
        // NOTE: make sure to have at least 200 MB of free disk space.
        // NOTE: This script will create lots of folders and files here,
        //       your file browser might not like folders with 3500+ files in one folder.
        String export = "C:/Users/Jimi/jagexcacheextract/maps";
        // Path and filename where to export a list of all icons on the map.
        // This file is formated in GEOJson format. (file will be about 30000 lines)
        String iconExport = "C:/Users/Jimi/jagexcacheextract/icons-locations.json";
        // Path and filename where to export a list of all labels.
        // TODO: This file is not used at the moment. Still need to be implemented.
        String labelExport = "C:/Users/Jimi/jagexcacheextract/labels.json";
        // Path and filename where to export a list of all (filtered) objects on map.
        // NOTE: This was used for pathfinding. Currently it exports all doors and walls.
        // NOTE: This will create a very large file! (~1 400 000 lines)
        String objectExport = "C:/Users/Jimi/jagexcacheextract/objects.json";
        // Path and filename where to export a list of all basemaps.
        // This is used by the copy data from this file to the Leaflet project.
        // This is used to load the maps and have the right cacheVersion.
        // NOTE: Manually check and copy the dat for now.
        String basemapsExport = "C:/Users/Jimi/jagexcacheextract/basemaps.json";

        String chunkPositionExport = "C:/Users/Jimi/jagexcacheextract/chunkpos.json";

        // Select what to export.
        boolean createIconExport = false;
        boolean createLabelExport = false;
        boolean createObjectExport = false;
        boolean createBasemapsExport = false;
        boolean createChunkPositionExport = false;
        boolean createMapImages = true;
        boolean moveTileOnWorldMap = false;
        //boolean limitBorders = false;

        // String used for cacheVersion in folders and json.
        String cacheBreaker = "_2021-09-02_1";

        // The mapID that changes what map is rendered.
        int mapID = 0;
        boolean renderAllMaps = true;

        // Checklist for rendering new maps:
        // 1. Find out what need to be changed.
        //    Did only the main map change or did any of the other map layers change too?
        // 2. Update the Runescape client and map sure paths above are set correctly.
        // 3. Download new Xtea keys for the the version of the client currently installed.
        //    It might take a day for the new keys to be added.
        //    Keys can be found here: https://archive.runestats.com/osrs/
        //    Copy the xteas.json file over to the location in:
        //    runelite/http-api/src/main/java/net/runelite/http/api/xtea/XteaClient.java for xteas keys: line 97
        //    Line 97: HttpUrl url = HttpUrl.parse("http://localhost/RSMap/RegionKeys.json");
        //    NOTE: This path has to be provided by a server (to get a propper HTTP responce)
        // 4. Make sure the map boundries are set correctly. This might change as the OSRS map gets bigger.
        // 5. Select what you want to export.

        Store store = loadStore(cache);

        MapImageDumper mapImageDumper = new MapImageDumper(store);

        // Set buffers for writing files.
        BufferedWriter iconWriter = null; // defined later
        BufferedWriter labelWriter = new BufferedWriter(new FileWriter(labelExport));
        BufferedWriter objectWriter = new BufferedWriter(new FileWriter(objectExport));
        BufferedWriter basemapsWriter = new BufferedWriter(new FileWriter(basemapsExport));
        BufferedWriter chunkposWriter = new BufferedWriter(new FileWriter(chunkPositionExport));

        BufferedImage img;
        BufferedImage groundFloorImg;

        // Map boundries, code will run only for the area defined here.
        // OSRS regions on 2019/02/02 form x:18 y:19 to x:66 y:163
        int xmin = 0, xmax = 80; // x min and max for map
        int ymin = 0, ymax = 180; // y min and max for map
        int zmin = 0, zmax = 4; // z, upper floor spaces, between 0 and 3 (zmax = 4 renders floor 3)
        int lmin = 4, lmax = 5; // layer or zoom level of map (default lmin = 2, lmax = 3 aka 4 pixel per map square)

        // Can be used for debugging code.
        // Makes code run only over a small area.
        boolean debugUsingSmallMap = false;
        if(debugUsingSmallMap){
            // Can be used for testing
            xmin = 40; xmax = 42;
            ymin = 51; ymax = 52;
            lmin = 2; lmax = 3;
        }

        // Set variables to be used later
        int minX = xmax;
        int minY = ymax;
        int maxX = xmin;
        int maxY = ymin;

        int x = 0;
        int y = 0;
        int z = 0;
        int layer = 0;

        // Change settings in mapImageDumper.
        // TODO: create something that works nicer. A bit hacky now.
        mapImageDumper.setLabelExportFile(labelWriter);
        mapImageDumper.setObjectExportFile(objectWriter);
        mapImageDumper.setLabelRegions(true);
        mapImageDumper.setOutlineRegions(true);

        // Worldmap (regions that are grouped together)
        Index index = store.getIndex(IndexType.WORLDMAP);
        Archive archive = index.getArchive(0); // There is also archive 1/2, but their data format is different

        System.out.println("archiveId: "+archive.getArchiveId());
        System.out.println("nameHash: "+archive.getNameHash());
        System.out.println("crc: "+archive.getCrc());
        System.out.println("revision: "+archive.getRevision());
        System.out.println("compression: "+archive.getCompression());
        System.out.println("fileData: "+archive.getFileData());
        System.out.println("hash: "+archive.getHash());

        Storage storage = store.getStorage();
        byte[] archiveData = storage.loadArchive(archive);
        ArchiveFiles files = archive.getFiles(archiveData);

        System.out.println("ARCHIVEFILES: "+files.getFiles().size());

        // Create json files manually
        // TODO: find better way to do this
        if (createIconExport) {
            iconWriter = new BufferedWriter(new FileWriter(iconExport));
            mapImageDumper.setIconsExportFile(iconWriter);
            iconWriter.write("{\n" +
                    "  \"type\": \"FeatureCollection\",\n" +
                    "  \"features\": [");
        }

        if (createObjectExport)
            objectWriter.write("{\n  \"items\": [");

        if (createBasemapsExport)
            basemapsWriter.write("{\"baseMaps\":[");

        if(createChunkPositionExport)
            chunkposWriter.write("{\n   \"chunkPos\":[");

        // Create folders for exporting tile images, if not existing already
        if (createMapImages) {
            File directory = new File(export);
            if (! directory.exists()){
                directory.mkdirs();
            }
        }
        int chunkWriteCount = 0;

        // Loop over all maps (called baseMaps in Cartographer project)
        for (FSFile file : files.getFiles())
        {
            // Load data for this map
            WorldMapLoader loader = new WorldMapLoader();
            WorldMapDefinition wmd = loader.load(file.getContents(), file.getFileId());

            if(renderAllMaps)
            {
                // if we render all maps, we render all maps above the selected MAP ID (set it to 0 to render all maps)
                if(wmd.getFileId() < mapID)
                    continue;
            }
            else
            {
                // if we only wanna render 1 specific map (mapID), just render that one
                if(wmd.getFileId() != mapID)
                    continue;
            }

            // Get the name of the map
            String mapName = wmd.getName();
            // Get string safe map name
            String mapSafeName = wmd.getSafeName();
            System.out.println(mapName);

            // Write info about map to json (bounds, zoom, center,...)
            if (createBasemapsExport) {
                basemapsWriter.write(wmd.getDefinitionBaseMap());
                continue;
            }

            if(createChunkPositionExport)
            {
                if(chunkWriteCount > 0)
                {
                    // append the comma between array entries
                    chunkposWriter.write(",\n");
                }
                chunkWriteCount++;
                chunkposWriter.write("\n        {\n" +
                        "           \"mapId\": "+ wmd.getFileId() +",\n" +
                        "           \"name\": \""+ wmd.getName() +"\",\n" +
                        "           \"chunks\":[\n");
            }

            // Loop over al the layers, these are the different zoom levels
            // layers only positive, 0, 1, 2, 3, 4 and 5
            int chunkPosWriteCount = 0;
            for (layer = lmin; layer <= lmax; layer++) {
                // Set proper zoom levels
                int zoom = (int) Math.pow(2, layer);
                mapImageDumper.setZoom(zoom);
                mapImageDumper.load();
                System.out.println("Loaded layer: " +layer);

                // Loop over all floors / plane / z
                for (z = zmin; z < zmax; z++) {
                    // Loop over map width
                    for (x = xmin; x < xmax; x++) {
                        // Loop over map height
                        for (y = ymin; y < ymax; y++) {

                            if(!wmd.containsRegion(x, y, z)){
                                // skip if region not in map
                                continue;
                            }
                            // Indicate progress when creating map
                            //System.out.print(".");

                            Region reg = mapImageDumper.getRegion(x, y);
                            if (reg == null) {
                                //System.out.println("No Region in: x:" + x + " y:" + y);
                            } else {
                                //System.out.println("Region in: x:" + x + " y:" + y);
                                // Update map bounds, just for debugging
                                minX = x < minX ? x : minX;
                                minY = y < minY ? y : minY;
                                maxX = x > maxX ? x : maxX;
                                maxY = y > maxY ? y : maxY;
                            }
                            // The position of the region on the map might not be the
                            // position of the map in game (game coordinates)
                            Position newPos = wmd.getNewPositionOfRegion(x,y,z);

                            if (createIconExport && layer == 2) {
                                // TODO: have to take into account the movement of tiles in WMD
                                mapImageDumper.exportMapIcons(reg, z);
                            }
                            if (createLabelExport && layer == 2) {
                                // TODO: have to take into account the movement of tiles in WMD
                                mapImageDumper.exportMapLabels(reg, z);
                            }
                            if (createObjectExport && layer == 2) {
                                // TODO: have to take into account the movement of tiles in WMD
                                mapImageDumper.exportMapObjects(reg, z);
                            }

                            // Draw image of region
                            img = mapImageDumper.drawRegion(reg, z);

                            // If map is a floor other then floor 0 a background is added to the image
                            // This gives indication of location when looking at higher floors
                            groundFloorImg = img;
                            if(z != 0){
                                // Create blurred map of ground floor and add current floor over it.
                                Graphics2D g = groundFloorImg.createGraphics();
                                // background
                                BufferedImage background = mapImageDumper.drawBlurredRegion(reg, 0);
                                g.drawImage(background, 0, 0, null);
                                // current floor
                                BufferedImage floor = mapImageDumper.drawTransparentRegion(reg, z);
                                g.drawImage(floor, 0, 0, null);
                                g.dispose();
                                // groundFloorImg = mapImageDumper.drawBlurredRegion(reg, 0);
                            }
                            if(createChunkPositionExport && layer <= lmin)
                            {
                                if(chunkPosWriteCount > 0)
                                {
                                    // append the comma between array entries
                                    chunkposWriter.write(",\n");
                                }
                                chunkPosWriteCount++;
                                chunkposWriter.write("               {\n" +
                                        "                   \"x\": "+ x +",\n" +
                                        "                   \"y\": "+ y +",\n" +
                                        "                   \"z\": "+ z +"\n" +
                                        "               }");
                            }


                            // Write image to file
                            if (createMapImages) {
                                int newX = x, newY = y, newZ = z;
                                if(moveTileOnWorldMap){
                                    newX = newPos.getX();
                                    newY = newPos.getY();
                                    newZ = newPos.getZ();
                                }
                                // Create correct filename and location
                                String directoryName = export + wmd.getFileId() + cacheBreaker + "/" + layer + "/";
                                String fullPath = directoryName + newZ + "_" + newX + "_" + newY + ".png";
                                File directory = new File(directoryName);
                                if (! directory.exists()){
                                    directory.mkdirs();
                                }
                                File png = new File(fullPath);
                                // Write image
                                //ImageIO.write(img, "png", png);
                                ImageIO.write(groundFloorImg, "png", png);

                                // Also add region to original position if on different plane / z / floor.
                                if(newZ != z && z != 0){
                                    File png2 = new File(directoryName + z + "_" + x + "_" + y + ".png");
                                    ImageIO.write(img, "png", png2);
                                }
                            }
                        }
                        //System.out.print("\n");
                    }
                }

                System.out.println("Layer " + layer + " is saved.");
            }

            if (createChunkPositionExport) {
                chunkposWriter.write("\n            ]\n        }");
            }
        }

        // Close all json files
        if (createIconExport) {
            iconWriter.write("\n    ]\n}");
            iconWriter.close();
        }

        if (createObjectExport) {
            objectWriter.write("\n  ]\n}");
            objectWriter.close();
        }

        if (createBasemapsExport) {
            basemapsWriter.write("\n  ]\n}");
            basemapsWriter.close();
        }

        if (createChunkPositionExport) {
            chunkposWriter.write("\n    ]\n}");
            chunkposWriter.close();
        }

        System.out.println("Map with region from x:" + minX + " y:" + minY);
        System.out.println("Map with region to   x:" + maxX + " y:" + maxY);

    }

//    private static void drawRegionID(BufferedImage img, Region reg){
//        if (reg != null) {
//            Graphics2D graphics = img.createGraphics();
//
//            graphics.setColor(Color.RED);
//            String str = baseX + "," + baseY + " (" + reg.getRegionX() + "," + reg.getRegionY() + ")";
//            graphics.drawString(str, drawBaseX * MAP_SCALE, drawBaseY * MAP_SCALE + graphics.getFontMetrics().getHeight());
//
//            graphics.setColor(Color.WHITE);
//            graphics.drawRect(drawBaseX * MAP_SCALE, drawBaseY * MAP_SCALE, Region.X * MAP_SCALE, Region.Y * MAP_SCALE);
//
//            graphics.dispose();
//        }
//    }

    private static Store loadStore(String cache) throws IOException
    {
        Store store = new Store(new File(cache));
        store.load();
        return store;
    }

    private void loadRegions(Store store) throws IOException
    {
        regionLoader = new RegionLoader(store);
        regionLoader.loadRegions();
        regionLoader.calculateBounds();

        logger.info("North most region: {}", regionLoader.getLowestY().getBaseY());
        logger.info("South most region: {}", regionLoader.getHighestY().getBaseY());
        logger.info("West most region:  {}", regionLoader.getLowestX().getBaseX());
        logger.info("East most region:  {}", regionLoader.getHighestX().getBaseX());
    }
}
