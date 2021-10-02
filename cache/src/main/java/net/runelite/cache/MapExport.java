package net.runelite.cache;

import com.google.gson.Gson;
import net.runelite.cache.definitions.loaders.WorldMapLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.RegionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import javax.imageio.*;
import net.runelite.cache.region.Region;
import net.runelite.cache.definitions.WorldMapDefinition;

// Alot of code is copied from these repos
//
// https://github.com/leejt/mapgen/blob/master/cache/src/main/java/net/runelite/cache/MapExport.java
// https://gitlab.com/weirdgloop/map-tile-generator/

public class MapExport
{
    private static String cache = "C:/Users/Jimi/jagexcache/oldschool/LIVE/";

    private static String basefolder = "C:/Users/Jimi/output/";

    public static void main(String[] args) throws Exception
    {
        for(int i = 0; i < args.length; i++)
        {
            switch(i)
            {
                case 0: // input folder (cache)
                    cache = args[i] + "/";
                    break;
                case 1: //  output folder
                    basefolder = args[i] + '/';
                    break;

            }
            System.out.println(i + " - " + args[i]);
        }
        Store store = new Store(new File(cache));
        store.load();

        writeWorldMap(store);
        //writeWorldMapDefinitions(store);
        return;
    }

    private static void writeWorldMap(Store store) throws Exception
    {
        MapImageDumper mapImageDumper = new MapImageDumper(store);

        RegionLoader regionLoader = new RegionLoader(store);
        regionLoader.loadRegions();

        List<ChunkPos> regionPos = new ArrayList<ChunkPos>();

        for(int zoomLayer = 0; zoomLayer < 3; zoomLayer++)
        {
            int zoom = (int)Math.pow(2, zoomLayer);
            mapImageDumper.setZoom(zoom);
            mapImageDumper.load();

            for (Region region : regionLoader.getRegions())
            {
                int x = region.getRegionX();
                int y = region.getRegionY();

                for (int plane = 0; plane < 4; plane++)
                {
                    BufferedImage reg = mapImageDumper.drawRegion(region, plane, plane > 0); // draw transparent planes if above 0
                    boolean isBlack = true;
                    for(int imgX = 0; imgX < reg.getWidth(); imgX++)
                    {
                        for(int imgY = 0; imgY < reg.getHeight(); imgY++)
                        {
                            int pixel = reg.getRGB(imgX, imgY);
                            if(pixel != (plane>0?0x00000000:0xFF000000))
                            {
                                isBlack = false;
                                break;
                            }
                        }
                        if(!isBlack)
                            break;
                    }

                    if(isBlack)
                    {
                        System.out.println("IMAGE WAS EMPTY: " + String.format("%s_%s_%s.png", plane, x, y));
                        continue;
                    }

                    // only add chunk pos
                    if(zoomLayer == 0)
                        regionPos.add(new ChunkPos(x, y, plane));

                    // comment out the 5 lines under this comment to only export regionpos.json
                    String filename = String.format("%s_%s_%s.png", plane, x, y);
                    File outputfile = new File(basefolder + "/" + zoomLayer + "/" + filename);
                    System.out.println(outputfile);
                    ImageIO.write(reg, "png", outputfile);
                }
            }
        }

        Gson gson = new Gson();
        PrintWriter out = new PrintWriter(basefolder + "regionpos.json");
        String json = gson.toJson(regionPos);
        out.write(json);
        out.close();
    }

    private static void writeWorldMapDefinitions(Store store) throws Exception
    {
        List<WorldMapDefinition> definitions = new ArrayList<WorldMapDefinition>();
        WorldMapLoader loader = new WorldMapLoader();
        Index index = store.getIndex(IndexType.WORLDMAP);
        Archive archive = index.getArchive(0);
        Storage storage = store.getStorage();
        byte[] archiveData = storage.loadArchive(archive);
        ArchiveFiles files = archive.getFiles(archiveData);
        for (FSFile file : files.getFiles()) {
            WorldMapDefinition wmd = loader.load(file.getContents(), file.getFileId());
            definitions.add(wmd);
        }

        Gson gson = new Gson();
        PrintWriter out = new PrintWriter(basefolder + "mapdef.json");
        String json = gson.toJson(definitions);
        out.write(json);
        out.close();
    }
}
