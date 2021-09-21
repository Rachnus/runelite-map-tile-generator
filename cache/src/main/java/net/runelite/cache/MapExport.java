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

// https://github.com/leejt/mapgen/blob/master/cache/src/main/java/net/runelite/cache/MapExport.java

public class MapExport
{
    public static void main(String[] args) throws Exception
    {
        String cache = "C:/Users/Jimi/jagexcache_2021-09-02/";
        Store store = new Store(new File(cache));
        store.load();


        writeWorldMap(store);
        //writeWorldMapDefinitions(store);
    }

    private static void writeWorldMap(Store store) throws Exception
    {
        MapImageDumper mapImageDumper = new MapImageDumper(store);
        mapImageDumper.load();

        RegionLoader regionLoader = new RegionLoader(store);
        regionLoader.loadRegions();

        List<ChunkPos> chunkPos = new ArrayList<ChunkPos>();

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

                chunkPos.add(new ChunkPos(x, y, plane));

                // comment out the 5 lines under this comment to only export regionpos.json
                String dirname = String.format("C:\\Users\\Jimi\\jagexcacheextract\\mapsTest\\");
                String filename = String.format("%s_%s_%s.png", plane, x, y);
                File outputfile = new File(dirname + filename);
                System.out.println(outputfile);
                ImageIO.write(reg, "png", outputfile);
            }
        }

        Gson gson = new Gson();
        String outputfile = String.format("C:\\Users\\Jimi\\jagexcacheextract\\mapsTest\\regionpos.json");
        PrintWriter out = new PrintWriter(outputfile);
        String json = gson.toJson(chunkPos);
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
        String outputfile = String.format("C:\\Users\\Jimi\\jagexcacheextract\\mapsTest\\mapdef.json");
        PrintWriter out = new PrintWriter(outputfile);
        String json = gson.toJson(definitions);
        out.write(json);
        out.close();
    }
}
