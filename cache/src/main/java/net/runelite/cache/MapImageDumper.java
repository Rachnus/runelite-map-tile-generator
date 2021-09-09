/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import net.runelite.cache.definitions.*;
import net.runelite.cache.definitions.loaders.IconMaperLoader;
import net.runelite.cache.definitions.loaders.OverlayLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.UnderlayLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.ColorPalette;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.Djb2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapImageDumper
{
	private static final Logger logger = LoggerFactory.getLogger(MapImageDumper.class);

	private int MAP_SCALE = 4; // this squared is the number of pixels per map square
	private int MAPICON_MAX_WIDTH = 5; // scale minimap icons down to this size so they fit..
	private int MAPICON_MAX_HEIGHT = 6;
	private static final int BLEND = 5; // number of surrounding tiles for ground blending

	private static int[] colorPalette = new ColorPalette(0.9d, 0, 512).getColorPalette();

	private final int wallColor = (255 << 24) + // Alpha
			(238 + (int) (Math.random() * 20.0D) - 10 << 16) + // Red
			(238 + (int) (Math.random() * 20.0D) - 10 << 8) + // Green
			(238 + (int) (Math.random() * 20.0D) - 10); // Blue
	private final int doorColor = (255 << 24) + // Alpha
			(238 + (int) (Math.random() * 20.0D) - 10 << 16); // Red

	private final Store store;

	private final Map<Integer, UnderlayDefinition> underlays = new HashMap<>();
	private final Map<Integer, OverlayDefinition> overlays = new HashMap<>();
	private final Map<Integer, Image> scaledMapIcons = new HashMap<>();

	private RegionLoader regionLoader;
	private final AreaManager areas;
	private final SpriteManager sprites;
	private RSTextureProvider rsTextureProvider;
	private final ObjectManager objectManager;
	private IconMaperLoader iconMaperLoader;

	@Getter
	@Setter
	private BufferedWriter iconsExportFile;
	private boolean firstIconLocation;

	@Getter
	@Setter
	private BufferedWriter labelExportFile;

	@Getter
	@Setter
	private BufferedWriter objectExportFile;
	private boolean firstObjectLocation;

	@Getter
	@Setter
	private boolean labelRegions;

	@Getter
	@Setter
	private boolean outlineRegions;

	public MapImageDumper(Store store)
	{
		this.store = store;
		this.areas = new AreaManager(store);
		this.sprites = new SpriteManager(store);
		objectManager = new ObjectManager(store);
		firstIconLocation = true;
		firstObjectLocation = true;
	}

	public void setZoom(int zoom){
		MAP_SCALE = zoom; // default = 4
		MAPICON_MAX_WIDTH = zoom+(int)Math.ceil(0.75*zoom); // default = 5
		MAPICON_MAX_HEIGHT = zoom+(int)Math.ceil(1*zoom); // default = 6
	}

	public void load() throws IOException
	{
		loadUnderlays(store);
		loadOverlays(store);
		objectManager.load();

		TextureManager textureManager = new TextureManager(store);
		textureManager.load();
		rsTextureProvider = new RSTextureProvider(textureManager, sprites);

		loadRegions(store);
		areas.load();
		sprites.load();
		loadSprites();

		TileShapes.load();
		//sprites.export(new File("./icons/"));

		// Load icon mapper
		iconMaperLoader = new IconMaperLoader();
		iconMaperLoader.load();


		//System.out.println(areas.getArea(98).name);
	}



	public Region getRegion(int x, int y){
		for (Region region : regionLoader.getRegions()) {
			int rx = region.getRegionX();
			int ry = region.getRegionY();
			if(x == rx && y == ry){
				return region;
			}
		}
		return null;
	}

	public BufferedImage drawMap(int z)
	{
		int minX = regionLoader.getLowestX().getBaseX();
		int minY = regionLoader.getLowestY().getBaseY();

		int maxX = regionLoader.getHighestX().getBaseX() + Region.X;
		int maxY = regionLoader.getHighestY().getBaseY() + Region.Y;

		int dimX = maxX - minX;
		int dimY = maxY - minY;

		int pixelsX = dimX * MAP_SCALE;
		int pixelsY = dimY * MAP_SCALE;

		logger.info("Map image dimensions: {}px x {}px, {}px per map square ({} MB). Max memory: {}mb", pixelsX, pixelsY,
				MAP_SCALE, (pixelsX * pixelsY * 3 / 1024 / 1024),
				Runtime.getRuntime().maxMemory() / 1024L / 1024L);

		int sizeImg = (pixelsX * pixelsY * 3 / 1024 / 1024);
		long tempmem = (long) (Runtime.getRuntime().maxMemory() / 1024L / 1024L);

		BufferedImage image = new BufferedImage(pixelsX, pixelsY, BufferedImage.TYPE_INT_RGB);

		drawMap(image, z);
		drawObjects(image, z);
		drawMapIcons(image, z);

		return image;
	}

	public BufferedImage drawRegion(Region region, int z)
	{
		int pixelsX = Region.X * MAP_SCALE;
		int pixelsY = Region.Y * MAP_SCALE;

		BufferedImage image = new BufferedImage(pixelsX, pixelsY, BufferedImage.TYPE_INT_RGB);


		if(region != null) {
			drawMap(image, 0, 0, z, region);
			drawObjects(image, 0, 0, region, z);

			//drawMapIcons(image, 0, 0, region, z);
		}

		return image;
	}

	public BufferedImage drawTransparentRegion(Region region, int z)
	{
		int pixelsX = Region.X * MAP_SCALE;
		int pixelsY = Region.Y * MAP_SCALE;

		BufferedImage image = new BufferedImage(pixelsX, pixelsY, BufferedImage.TYPE_INT_ARGB);


		if(region != null) {
			drawMap(image, 0, 0, z, region);
			drawObjects(image, 0, 0, region, z);
			//drawMapIcons(image, 0, 0, region, z);
		}

		return image;
	}

	public BufferedImage drawBlurredRegion(Region region, int z)
	{
		// Blur: Gaussian blur 5x5: http://dev.theomader.com/gaussian-kernel-calculator/
		float[] matrix = {
				0.000354f, 0.000649f, 0.001065f, 0.001565f, 0.002061f, 0.002431f, 0.002568f, 0.002431f, 0.002061f, 0.001565f, 0.001065f, 0.000649f, 0.000354f,
				0.000649f, 0.001188f, 0.00195f, 0.002867f, 0.003776f, 0.004454f, 0.004706f, 0.004454f, 0.003776f, 0.002867f, 0.00195f, 0.001188f, 0.000649f,
				0.001065f, 0.00195f, 0.003201f, 0.004706f, 0.006196f, 0.007309f, 0.007723f, 0.007309f, 0.006196f, 0.004706f, 0.003201f, 0.00195f, 0.001065f,
				0.001565f, 0.002867f, 0.004706f, 0.006918f, 0.009109f, 0.010745f, 0.011353f, 0.010745f, 0.009109f, 0.006918f, 0.004706f, 0.002867f, 0.001565f,
				0.002061f, 0.003776f, 0.006196f, 0.009109f, 0.011995f, 0.014149f, 0.01495f, 0.014149f, 0.011995f, 0.009109f, 0.006196f, 0.003776f, 0.002061f,
				0.002431f, 0.004454f, 0.007309f, 0.010745f, 0.014149f, 0.01669f, 0.017634f, 0.01669f, 0.014149f, 0.010745f, 0.007309f, 0.004454f, 0.002431f,
				0.002568f, 0.004706f, 0.007723f, 0.011353f, 0.01495f, 0.017634f, 0.018632f, 0.017634f, 0.01495f, 0.011353f, 0.007723f, 0.004706f, 0.002568f,
				0.002431f, 0.004454f, 0.007309f, 0.010745f, 0.014149f, 0.01669f, 0.017634f, 0.01669f, 0.014149f, 0.010745f, 0.007309f, 0.004454f, 0.002431f,
				0.002061f, 0.003776f, 0.006196f, 0.009109f, 0.011995f, 0.014149f, 0.01495f, 0.014149f, 0.011995f, 0.009109f, 0.006196f, 0.003776f, 0.002061f,
				0.001565f, 0.002867f, 0.004706f, 0.006918f, 0.009109f, 0.010745f, 0.011353f, 0.010745f, 0.009109f, 0.006918f, 0.004706f, 0.002867f, 0.001565f,
				0.001065f, 0.00195f, 0.003201f, 0.004706f, 0.006196f, 0.007309f, 0.007723f, 0.007309f, 0.006196f, 0.004706f, 0.003201f, 0.00195f, 0.001065f,
				0.000649f, 0.001188f, 0.00195f, 0.002867f, 0.003776f, 0.004454f, 0.004706f, 0.004454f, 0.003776f, 0.002867f, 0.00195f, 0.001188f, 0.000649f,
				0.000354f, 0.000649f, 0.001065f, 0.001565f, 0.002061f, 0.002431f, 0.002568f, 0.002431f, 0.002061f, 0.001565f, 0.001065f, 0.000649f, 0.000354f
		};
		Kernel kernel = new Kernel(13, 13, matrix);
		BufferedImageOp op = new ConvolveWithEdgeOp(kernel, ConvolveWithEdgeOp.EDGE_REFLECT, null);
		ColorConvertOp colorConvert =
				new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
		RescaleOp brightOp = new RescaleOp(1.9f, 0, null);

		int pixelsX = Region.X * MAP_SCALE;
		int pixelsY = Region.Y * MAP_SCALE;

		BufferedImage image = new BufferedImage(pixelsX, pixelsY, BufferedImage.TYPE_INT_RGB);


		if(region != null) {
			drawMap(image, 0, 0, z, region);
			drawObjects(image, 0, 0, region, z);
			//drawMapIcons(image, 0, 0, region, z);
		}
		// add blur

		image = op.filter(image, null);
		image = colorConvert.filter(image, null);
		image = brightOp.filter(image, null);

		return image;
	}

	public void exportMapIcons(Region region, int z){
		if(region != null /*&& z == 2*/) { // TODO Z level
			try {
				exportMapIconsData(iconsExportFile, 0, 0, region, z);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void exportMapLabels(Region region, int z){
		if(region != null /*&& z == 2*/) { // TODO Z level
			try {
				exportMapLabelData(labelExportFile, 0, 0, region, z);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void exportMapObjects(Region region, int z){
		if(region != null) {
			try {
				exportMapObjectData(objectExportFile, 0, 0, region, z);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void exportMapIconsData(BufferedWriter writer, int drawBaseX, int drawBaseY, Region region, int z) throws IOException {
		for (Location location : region.getLocations())
		{
			int localZ = location.getPosition().getZ();
			if (z != 0 && localZ != z)
			{
				// draw all icons on z=0 and on there own layer
				continue;
			}

			ObjectDefinition od = findObject(location.getId());

			assert od != null;

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			int drawX = drawBaseX + localX;
			int drawY = drawBaseY + (Region.Y - 1 - localY);

			if (od.getMapAreaId() != -1)
			{
				AreaDefinition area = areas.getArea(od.getMapAreaId());
				assert area != null;
				//System.out.println("Area:" + area.name);

				int spriteId = area.spriteId;

				SpriteDefinition sprite = sprites.findSprite(spriteId, 0);
				assert sprite != null;

				String iconName = "IconNotFound";
				IconMapDefinition imd = iconMaperLoader.getIconByID(sprite.getId());
				if(imd != null){
					iconName = imd.getName();
				}


				//BufferedImage iconImage = sprites.getSpriteImage(sprite);
				//graphics.drawImage(iconImage, drawX * MAP_SCALE-15/2, drawY * MAP_SCALE-15/2, null);
				if(!firstIconLocation){
					writer.write(",");
				}
				firstIconLocation = false;
//				writer.write("{\n" +
//					"      \"icon\": \""+iconName+"-0\",\n" +
//					"      \"x\": "+ (location.getPosition().getX()) +",\n" +
//					"      \"y\": "+ (location.getPosition().getY()) +",\n" +
//					"      \"plane\": "+ z +"\n" +
//					"    }");

				writer.write("{\n" +
						"      \"type\": \"Feature\",\n" +
						"      \"properties\": {\n" +
						"        \"providerID\": 1,\n" +
						"        \"icon\": \""+iconName+"\",\n" +
						"        \"mapID\": 0\n" +
						"      },\n" +
						"      \"geometry\": {\n" +
						"        \"type\": \"Point\",\n" +
						"        \"coordinates\": [\n" +
						"          "+ (location.getPosition().getX()) +",\n" +
						"          "+ (location.getPosition().getY()) +",\n" +
						"          "+ z +"\n" +
						"        ]\n" +
						"      }\n" +
						"    }");


			}
		}
	}

	private void exportMapLabelData(BufferedWriter writer, int drawBaseX, int drawBaseY, Region region, int z) throws IOException {
		for (Location location : region.getLocations())
		{
			int localZ = location.getPosition().getZ();
			if (z != 0 && localZ != z)
			{
				// draw all labels on z=0 and on there own layer
				continue;
			}

			ObjectDefinition od = findObject(location.getId());

			assert od != null;

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			int drawX = drawBaseX + localX;
			int drawY = drawBaseY + (Region.Y - 1 - localY);

			if (od.getMapAreaId() != -1)
			{
				AreaDefinition area = areas.getArea(od.getMapAreaId());
				assert area != null;
				//System.out.println("Area:" + area.name);

				int spriteId = area.spriteId;

				SpriteDefinition sprite = sprites.findSprite(spriteId, 0);
				assert sprite != null;

				//BufferedImage iconImage = sprites.getSpriteImage(sprite);
				//graphics.drawImage(iconImage, drawX * MAP_SCALE-15/2, drawY * MAP_SCALE-15/2, null);
				if(!firstIconLocation){
					writer.write(",");
				}
				firstIconLocation = false;
				writer.write("{\n" +
						"      \"icon\": \""+sprite.getId()+"-0\",\n" +
						"      \"x\": "+ (location.getPosition().getX()) +",\n" +
						"      \"y\": "+ (location.getPosition().getY()) +",\n" +
						"      \"plane\": "+ z +"\n" +
						"    }");

			}
		}
	}

	private void exportMapObjectData(BufferedWriter writer, int drawBaseX, int drawBaseY, Region region, int z) throws IOException {
		for (Location location : region.getLocations())
		{
			int rotation = location.getOrientation();
			int type = location.getType();

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			boolean isBridge = (region.getTileSetting(1, localX, localY) & 2) != 0;

			if (location.getPosition().getZ() == z + 1)
			{
				if (!isBridge)
				{
					continue;
				}
			}
			else if (location.getPosition().getZ() == z)
			{
				if (isBridge)
				{
					continue;
				}

				if ((region.getTileSetting(z, localX, localY) & 24) != 0)
				{
					continue;
				}
			}
			else
			{
				continue;
			}

			ObjectDefinition object = findObject(location.getId());

			boolean door = false;
			boolean wall = false;

			if (type >= 0 && type <= 3) {
				// this is a wall
				int hash = (localY << 7) + localX + (location.getId() << 14) + 0x4000_0000;
				if (object.getWallOrDoor() == 0) {
					hash -= Integer.MIN_VALUE;
				}

				if (hash > 0) {
					door = true;
				}else {
					wall = true;
				}

				if (object.getMapSceneID() != -1)
				{
					//door = true;
					continue;
				}
			}else if (type == 9) {

				if (object.getMapSceneID() != -1)
				{
					//door = true;
					continue;
				}

				int hash = (localY << 7) + localX + (location.getId() << 14) + 0x4000_0000;
				if (object.getWallOrDoor() == 0) {
					hash -= Integer.MIN_VALUE;
				}

				if ((hash >> 29 & 3) != 2) {
					continue;
				}

				if (hash > 0) {
					door = true;
				}else{
					wall = true;
				}
			}else{
				continue;
			}

//			if(!door){
//				continue;
//			}

			if(!firstObjectLocation){
				writer.write(",");
			}
			firstObjectLocation = false;
			writer.write("{\n" +
					"      \"objectID\": \""+object.getId()+"\",\n" +
					"      \"objectName\": \""+object.getName()+"\",\n" +
					"      \"x\": "+ (location.getPosition().getX()) +",\n" +
					"      \"y\": "+ (location.getPosition().getY()) +",\n" +
					"      \"plane\": "+ z +",\n" +
					"      \"rotation\": "+ rotation +",\n" +
					"      \"type\": "+ type +",\n" +
					"      \"wall\": "+ wall +",\n" +
					"      \"door\": "+ door +",\n" +
					"      \"sceneID\": "+ object.getMapSceneID() +"\n" +
					"    }");

			String iconName = "145"+type+"-0";

//			writer.write("{\n" +
//				"      \"type\": \"icon\",\n" +
//				"      \"providerID\": 1,\n" +
//				"      \"x\": "+ (location.getPosition().getX()) +",\n" +
//				"      \"y\": "+ (location.getPosition().getY()) +",\n" +
//				"      \"plane\": "+ z +",\n" +
//				"      \"icon\": \""+iconName+"\"\n" +
//				"    }");
		}
	}

	private void drawMap(BufferedImage image, int drawBaseX, int drawBaseY, int z, Region region)
	{
		int[][] map = new int[Region.X * MAP_SCALE][Region.Y * MAP_SCALE];
		drawMap(map, region, z);

		int[][] above = null;
		if (z < 3)
		{
			above = new int[Region.X * MAP_SCALE][Region.Y * MAP_SCALE];
			drawMap(above, region, z + 1);
		}

		for (int x = 0; x < Region.X; ++x)
		{
			for (int y = 0; y < Region.Y; ++y)
			{
				boolean isBridge = (region.getTileSetting(1, x, Region.Y - y - 1) & 2) != 0;

				int tileSetting = region.getTileSetting(z, x, Region.Y - y - 1);
				if (!isBridge && ((tileSetting & 24) == 0))
				{
					drawTile(image, map, drawBaseX, drawBaseY, x, y);
				}

				if (z < 3 && isBridge) // client also has a check for &8 != 0 here
				{
					drawTile(image, above, drawBaseX, drawBaseY, x, y);
				}
			}
		}
	}

	private void drawMap(BufferedImage image, int z)
	{
		for (Region region : regionLoader.getRegions())
		{
			int baseX = region.getBaseX();
			int baseY = region.getBaseY();

			// to pixel X
			int drawBaseX = baseX - regionLoader.getLowestX().getBaseX();

			// to pixel Y. top most y is 0, but the top most
			// region has the greatest y, so invert
			int drawBaseY = regionLoader.getHighestY().getBaseY() - baseY;

			drawMap(image, drawBaseX, drawBaseY, z, region);
		}
	}

	private void drawTile(BufferedImage to, int[][] pixels, int drawBaseX, int drawBaseY, int x, int y)
	{
		for (int i = 0; i < MAP_SCALE; ++i)
		{
			for (int j = 0; j < MAP_SCALE; ++j)
			{
				to.setRGB(drawBaseX * MAP_SCALE + x * MAP_SCALE + i,
						drawBaseY * MAP_SCALE + y * MAP_SCALE + j,
						pixels[x * MAP_SCALE + i][y * MAP_SCALE + j]);
			}
		}
	}

	private void drawMap(int[][] pixels, Region region, int z)
	{
		int baseX = region.getBaseX();
		int baseY = region.getBaseY();

		int len = Region.X + BLEND * 2;
		int[] hues = new int[len];
		int[] sats = new int[len];
		int[] light = new int[len];
		int[] mul = new int[len];
		int[] num = new int[len];

		boolean hasLeftRegion = regionLoader.findRegionForWorldCoordinates(baseX - 1, baseY) != null;
		boolean hasRightRegion = regionLoader.findRegionForWorldCoordinates(baseX + Region.X, baseY) != null;
		boolean hasUpRegion = regionLoader.findRegionForWorldCoordinates(baseX, baseY + Region.Y) != null;
		boolean hasDownRegion = regionLoader.findRegionForWorldCoordinates(baseX, baseY - 1) != null;

		for (int xi = (hasLeftRegion ? -BLEND * 2 : -BLEND); xi < Region.X + (hasRightRegion ? BLEND * 2 : BLEND); ++xi)
		{
			for (int yi = (hasDownRegion ? -BLEND : 0); yi < Region.Y + (hasUpRegion ? BLEND : 0); ++yi)
			{
				int xr = xi + BLEND;
				if (xr >= (hasLeftRegion ? -BLEND : 0) && xr < Region.X + (hasRightRegion ? BLEND : 0))
				{
					Region r = regionLoader.findRegionForWorldCoordinates(baseX + xr, baseY + yi);
					if (r != null)
					{
						int underlayId = r.getUnderlayId(z, convert(xr), convert(yi));
						if (underlayId > 0)
						{
							UnderlayDefinition underlay = findUnderlay(underlayId - 1);
							hues[yi + BLEND] += underlay.getHue();
							sats[yi + BLEND] += underlay.getSaturation();
							light[yi + BLEND] += underlay.getLightness();
							mul[yi + BLEND] += underlay.getHueMultiplier();
							num[yi + BLEND]++;
						}
					}
				}

				int xl = xi - BLEND;
				if (xl >= (hasLeftRegion ? -BLEND : 0) && xl < Region.X + (hasRightRegion ? BLEND : 0))
				{
					Region r = regionLoader.findRegionForWorldCoordinates(baseX + xl, baseY + yi);
					if (r != null)
					{
						int underlayId = r.getUnderlayId(z, convert(xl), convert(yi));
						if (underlayId > 0)
						{
							UnderlayDefinition underlay = findUnderlay(underlayId - 1);
							hues[yi + BLEND] -= underlay.getHue();
							sats[yi + BLEND] -= underlay.getSaturation();
							light[yi + BLEND] -= underlay.getLightness();
							mul[yi + BLEND] -= underlay.getHueMultiplier();
							num[yi + BLEND]--;
						}
					}
				}
			}

			if (xi >= 0 && xi < Region.X)
			{
				int runningHues = 0;
				int runningSat = 0;
				int runningLight = 0;
				int runningMultiplier = 0;
				int runningNumber = 0;

				for (int yi = (hasDownRegion ? -BLEND * 2 : -BLEND); yi < Region.Y + (hasUpRegion ? BLEND * 2 : BLEND); ++yi)
				{
					int yu = yi + BLEND;
					if (yu >= (hasDownRegion ? -BLEND : 0) && yu < Region.Y + (hasUpRegion ? BLEND : 0))
					{
						runningHues += hues[yu + BLEND];
						runningSat += sats[yu + BLEND];
						runningLight += light[yu + BLEND];
						runningMultiplier += mul[yu + BLEND];
						runningNumber += num[yu + BLEND];
					}

					int yd = yi - BLEND;
					if (yd >= (hasDownRegion ? -BLEND : 0) && yd < Region.Y + (hasUpRegion ? BLEND : 0))
					{
						runningHues -= hues[yd + BLEND];
						runningSat -= sats[yd + BLEND];
						runningLight -= light[yd + BLEND];
						runningMultiplier -= mul[yd + BLEND];
						runningNumber -= num[yd + BLEND];
					}

					if (yi >= 0 && yi < Region.Y)
					{
						Region r = regionLoader.findRegionForWorldCoordinates(baseX + xi, baseY + yi);
						if (r != null)
						{
							int underlayId = r.getUnderlayId(z, convert(xi), convert(yi));
							int overlayId = r.getOverlayId(z, convert(xi), convert(yi));

							if (underlayId > 0 || overlayId > 0)
							{
								int underlayHsl = -1;
								if (underlayId > 0)
								{
									int avgHue = runningHues * 256 / runningMultiplier;
									int avgSat = runningSat / runningNumber;
									int avgLight = runningLight / runningNumber;
									// randomness is added to avgHue here

									if (avgLight < 0)
									{
										avgLight = 0;
									}
									else if (avgLight > 255)
									{
										avgLight = 255;
									}

									underlayHsl = packHsl(avgHue, avgSat, avgLight);
								}

								int underlayRgb = 0;
								if (underlayHsl != -1)
								{
									int var0 = method1792(underlayHsl, 96);
									underlayRgb = colorPalette[var0];
								}

								int shape, rotation;
								Integer overlayRgb = null;
								if (overlayId == 0)
								{
									shape = rotation = 0;
								}
								else
								{
									shape = r.getOverlayPath(z, convert(xi), convert(yi)) + 1;
									rotation = r.getOverlayRotation(z, convert(xi), convert(yi));

									OverlayDefinition overlayDefinition = findOverlay(overlayId - 1);
									int overlayTexture = overlayDefinition.getTexture();
									int rgb;

									if (overlayTexture >= 0)
									{
										rgb = rsTextureProvider.getAverageTextureRGB(overlayTexture);
									}
									else if (overlayDefinition.getRgbColor() == 0xFF_00FF)
									{
										rgb = -2;
									}
									else
									{
										// randomness added here
										int overlayHsl = packHsl(overlayDefinition.getHue(), overlayDefinition.getSaturation(), overlayDefinition.getLightness());
										rgb = overlayHsl;
									}

									overlayRgb = 0;
									if (rgb != -2)
									{
										int var0 = adjustHSLListness0(rgb, 96);
										overlayRgb = colorPalette[var0];
									}

									if (overlayDefinition.getSecondaryRgbColor() != -1)
									{
										int hue = overlayDefinition.getOtherHue();
										int sat = overlayDefinition.getOtherSaturation();
										int olight = overlayDefinition.getOtherLightness();
										rgb = packHsl(hue, sat, olight);
										int var0 = adjustHSLListness0(rgb, 96);
										overlayRgb = colorPalette[var0];
									}
								}

								if (shape == 0)
								{
									int drawX = xi;
									int drawY = Region.Y - 1 - yi;
									if (underlayRgb != 0)
									{
										drawMapSquare(pixels, drawX, drawY, underlayRgb);
									}
								}
								else if (shape == 1)
								{
									int drawX = xi;
									int drawY = Region.Y - 1 - yi;
									drawMapSquare(pixels, drawX, drawY, overlayRgb);
								}
								else
								{
									// Draw curved edges and transition edges
									int drawX = xi * MAP_SCALE;
									int drawY = (Region.Y - 1 - yi) * MAP_SCALE;
									int[] tileShapes = TileShapes.getTileShape(MAP_SCALE, shape);
									int[] tileRotations = TileShapes.getTileRotation(MAP_SCALE, rotation);
									if (underlayRgb != 0)
									{
										// Overlay
										int rotIdx = 0;
										for (int i = 0; i < MAP_SCALE/*Region.Z*/; i++)
										{
											for (int s = 0; s < MAP_SCALE; s++) {
												int p1 = tileShapes[tileRotations[rotIdx++]] == 0 ? underlayRgb : overlayRgb;
												pixels[drawX + s][drawY + i] = p1;
											}
										}
									}
									else
									{
										// Underlay
										int rotIdx = 0;
										for (int i = 0; i < MAP_SCALE/*Region.Z*/; i++)
										{
											for (int s = 0; s < MAP_SCALE; s++) {
												int p1 = tileShapes[tileRotations[rotIdx++]];
												if (p1 != 0) {
													pixels[drawX + s][drawY + i] = overlayRgb;
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static int convert(int d)
	{
		if (d >= 0)
		{
			return d % 64;
		}
		else
		{
			return 64 - -(d % 64) - 1;
		}
	}

	private void drawObjects(BufferedImage image, int drawBaseX, int drawBaseY, Region region, int z)
	{
		Graphics2D graphics = image.createGraphics();

		for (Location location : region.getLocations())
		{
			int rotation = location.getOrientation();
			int type = location.getType();
			//System.out.println(type);
			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			boolean isBridge = (region.getTileSetting(1, localX, localY) & 2) != 0;

			if (location.getPosition().getZ() == z + 1)
			{
				if (!isBridge)
				{
					continue;
				}
			}
			else if (location.getPosition().getZ() == z)
			{
				if (isBridge)
				{
					continue;
				}

				if ((region.getTileSetting(z, localX, localY) & 24) != 0)
				{
					continue;
				}
			}
			else
			{
				continue;
			}

			ObjectDefinition object = findObject(location.getId());

			int drawX = (drawBaseX + localX) * MAP_SCALE;
			int drawY = (drawBaseY + (Region.Y - 1 - localY)) * MAP_SCALE;

			if (type >= 0 && type <= 3)
			{
				// this is a wall
				int hash = (localY << 7) + localX + (location.getId() << 14) + 0x4000_0000;

				if (object.getWallOrDoor() == 0)
				{
					hash -= Integer.MIN_VALUE;
				}

				int rgb = wallColor;
				if (hash > 0)
				{
					rgb = doorColor;
				}

				if (object.getMapSceneID() != -1)
				{
					Image spriteImage = scaledMapIcons.get(object.getMapSceneID());
					graphics.drawImage(spriteImage, drawX * MAP_SCALE, drawY * MAP_SCALE, null);
				}
				else
				{
					if (type == 0 || type == 2)
					{
						if (rotation == 0)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + 0, drawY + s, rgb);
							}
						}
						else if (rotation == 1)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + s, drawY + 0, rgb);
							}
						}
						else if (rotation == 2)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + (MAP_SCALE-1), drawY + s, rgb);
							}
						}
						else if (rotation == 3)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + s, drawY + (MAP_SCALE-1), rgb);
							}
						}
					}

					if (type == 3)
					{
						if (rotation == 0)
						{
							image.setRGB(drawX + 0, drawY + 0, rgb);
						}
						else if (rotation == 1)
						{
							image.setRGB(drawX + (MAP_SCALE-1), drawY + 0, rgb);
						}
						else if (rotation == 2)
						{
							image.setRGB(drawX + (MAP_SCALE-1), drawY + (MAP_SCALE-1), rgb);
						}
						else if (rotation == 3)
						{
							image.setRGB(drawX + 0, drawY + (MAP_SCALE-1), rgb);
						}
					}

					if (type == 2)
					{
						if (rotation == 3)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + 0, drawY + s, rgb);
							}
						}
						else if (rotation == 0)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + s, drawY + 0, rgb);
							}
						}
						else if (rotation == 1)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + (MAP_SCALE-1), drawY + s, rgb);
							}
						}
						else if (rotation == 2)
						{
							for (int s = 0; s < MAP_SCALE; s++) {
								image.setRGB(drawX + s, drawY + (MAP_SCALE-1), rgb);
							}
						}
					}
				}
			}
			else if (type == 9)
			{
				if (object.getMapSceneID() != -1)
				{
					Image spriteImage = scaledMapIcons.get(object.getMapSceneID());
					graphics.drawImage(spriteImage, drawX, drawY, null);
					continue;
				}

				int hash = (localY << 7) + localX + (location.getId() << 14) + 0x4000_0000;
				if (object.getWallOrDoor() == 0)
				{
					hash -= Integer.MIN_VALUE;
				}

				if ((hash >> 29 & 3) != 2)
				{
					continue;
				}

				int rgb = wallColor;
				if (hash > 0)
				{
					rgb = doorColor;
				}

				if (rotation != 0 && rotation != 2)
				{
					for (int s = 0; s < MAP_SCALE; s++) {
						image.setRGB(drawX + s, drawY + s, rgb);
					}
				}
				else
				{
					for (int s = 0; s < MAP_SCALE; s++) {
						image.setRGB(drawX + s, drawY + (MAP_SCALE-1-s), rgb);
					}
				}
			}
			else if (type == 22 || (type >= 9 && type <= 11))
			{
				// ground object
				if (object.getMapSceneID() != -1)
				{
					Image spriteImage = scaledMapIcons.get(object.getMapSceneID());
					graphics.drawImage(spriteImage, drawX, drawY, null);
				}
			}
		}

		graphics.dispose();
	}

	private void drawObjects(BufferedImage image, int z)
	{
		for (Region region : regionLoader.getRegions())
		{
			int baseX = region.getBaseX();
			int baseY = region.getBaseY();

			// to pixel X
			int drawBaseX = baseX - regionLoader.getLowestX().getBaseX();

			// to pixel Y. top most y is 0, but the top most
			// region has the greatest y, so invert
			int drawBaseY = regionLoader.getHighestY().getBaseY() - baseY;

			drawObjects(image, drawBaseX, drawBaseY, region, z);
		}
	}

	private void drawMapIcons(BufferedImage image, int drawBaseX, int drawBaseY, Region region, int z)
	{
		int baseX = region.getBaseX();
		int baseY = region.getBaseY();

		Graphics2D graphics = image.createGraphics();

		drawMapIcons(graphics, region, z, drawBaseX, drawBaseY);
		drawMapLabels(graphics, region, z, drawBaseX, drawBaseY);

		if (labelRegions)
		{
			graphics.setColor(Color.RED);
			String str = baseX + "," + baseY + " (" + region.getRegionX() + "," + region.getRegionY() + "), ID: " + region.getRegionID();
			graphics.drawString(str, drawBaseX * MAP_SCALE+3, drawBaseY * MAP_SCALE + graphics.getFontMetrics().getHeight());
		}

		if (outlineRegions)
		{
			graphics.setColor(Color.RED);
			graphics.drawRect(drawBaseX * MAP_SCALE, drawBaseY * MAP_SCALE, Region.X * MAP_SCALE, Region.Y * MAP_SCALE);
		}

		graphics.dispose();
	}

	private void drawMapIcons(BufferedImage image, int z)
	{
		// map icons
		for (Region region : regionLoader.getRegions())
		{
			int baseX = region.getBaseX();
			int baseY = region.getBaseY();

			// to pixel X
			int drawBaseX = baseX - regionLoader.getLowestX().getBaseX();

			// to pixel Y. top most y is 0, but the top most
			// region has the greatest y, so invert
			int drawBaseY = regionLoader.getHighestY().getBaseY() - baseY;

			drawMapIcons(image, drawBaseX, drawBaseY, region, z);
		}
	}

	private ObjectDefinition findObject(int id)
	{
		return objectManager.getObject(id);
	}

	private int packHsl(int var0, int var1, int var2)
	{
		if (var2 > 179)
		{
			var1 /= 2;
		}

		if (var2 > 192)
		{
			var1 /= 2;
		}

		if (var2 > 217)
		{
			var1 /= 2;
		}

		if (var2 > 243)
		{
			var1 /= 2;
		}

		int var3 = (var1 / 32 << 7) + (var0 / 4 << 10) + var2 / 2;
		return var3;
	}

	static int method1792(int var0, int var1)
	{
		if (var0 == -1)
		{
			return 12345678;
		}
		else
		{
			var1 = (var0 & 127) * var1 / 128;
			if (var1 < 2)
			{
				var1 = 2;
			}
			else if (var1 > 126)
			{
				var1 = 126;
			}

			return (var0 & 65408) + var1;
		}
	}

	static final int adjustHSLListness0(int var0, int var1)
	{
		if (var0 == -2)
		{
			return 12345678;
		}
		else if (var0 == -1)
		{
			if (var1 < 2)
			{
				var1 = 2;
			}
			else if (var1 > 126)
			{
				var1 = 126;
			}

			return var1;
		}
		else
		{
			var1 = (var0 & 127) * var1 / 128;
			if (var1 < 2)
			{
				var1 = 2;
			}
			else if (var1 > 126)
			{
				var1 = 126;
			}

			return (var0 & 65408) + var1;
		}
	}

	private void drawMapSquare(int[][] pixels, int x, int y, int rgb)
	{
		x *= MAP_SCALE;
		y *= MAP_SCALE;

//        double alpha = (double) (rgb >> 24) / 256.0D;
//        double red = (double) (rgb >> 16) / 256.0D;
//        double green = (double) (rgb >> 8 & 255) / 256.0D;
//        double blue = (double) (rgb & 255) / 256.0D;
//        System.out.println("" + alpha + " " + red + " " + green + " " + blue);

		for (int i = 0; i < MAP_SCALE; ++i)
		{
			for (int j = 0; j < MAP_SCALE; ++j)
			{
				pixels[x + i][y + j] = rgb;
			}
		}
	}

	private void drawMapIcons(Graphics2D graphics, Region region, int z, int drawBaseX, int drawBaseY)
	{
		for (Location location : region.getLocations())
		{
			int localZ = location.getPosition().getZ();
			if (z != 0 && localZ != z)
			{
				// draw all icons on z=0
				continue;
			}

			ObjectDefinition od = findObject(location.getId());

			assert od != null;

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			int drawX = drawBaseX + localX;
			int drawY = drawBaseY + (Region.Y - 1 - localY);

			if (od.getMapAreaId() != -1)
			{
				AreaDefinition area = areas.getArea(od.getMapAreaId());
				assert area != null;

				int spriteId = area.spriteId;

				SpriteDefinition sprite = sprites.findSprite(spriteId, 0);
				assert sprite != null;

				BufferedImage iconImage = sprites.getSpriteImage(sprite);
				graphics.drawImage(iconImage, drawX * MAP_SCALE-15/2, drawY * MAP_SCALE-15/2, null);
			}
		}
	}

	private void drawMapLabels(Graphics2D graphics, Region region, int z, int drawBaseX, int drawBaseY)
	{
		for (Location location : region.getLocations())
		{
			int localZ = location.getPosition().getZ();
			if (z != 0 && localZ != z)
			{
				// draw all icons on z=0
				continue;
			}

			ObjectDefinition od = findObject(location.getId());

			assert od != null;

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();

			int drawX = drawBaseX + localX;
			int drawY = drawBaseY + (Region.Y - 1 - localY);

			if (od.getMapAreaId() != -1)
			{
				AreaDefinition area = areas.getArea(od.getMapAreaId());
				assert area != null;

				String name = area.name;
				//System.out.println(area.toString());
				//assert name != null;
				if(name == null){
					continue;
				}
				System.out.println(name);

				graphics.setColor(Color.RED);
				graphics.drawString(name, drawBaseX * MAP_SCALE+3, drawBaseY * MAP_SCALE + 20 + graphics.getFontMetrics().getHeight());
			}
		}
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

	private void loadUnderlays(Store store) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index.getArchive(ConfigType.UNDERLAY.getId());

		byte[] archiveData = storage.loadArchive(archive);
		ArchiveFiles files = archive.getFiles(archiveData);

		for (FSFile file : files.getFiles())
		{
			UnderlayLoader loader = new UnderlayLoader();
			UnderlayDefinition underlay = loader.load(file.getFileId(), file.getContents());

			underlays.put(underlay.getId(), underlay);
		}
	}

	private UnderlayDefinition findUnderlay(int id)
	{
		return underlays.get(id);
	}

	private void loadOverlays(Store store) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index.getArchive(ConfigType.OVERLAY.getId());

		byte[] archiveData = storage.loadArchive(archive);
		ArchiveFiles files = archive.getFiles(archiveData);

		for (FSFile file : files.getFiles())
		{
			OverlayLoader loader = new OverlayLoader();
			OverlayDefinition overlay = loader.load(file.getFileId(), file.getContents());

			overlays.put(overlay.getId(), overlay);
		}
	}

	private OverlayDefinition findOverlay(int id)
	{
		return overlays.get(id);
	}

	private void loadSprites() throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.SPRITES);
		final int mapsceneHash = Djb2.hash("mapscene");

		for (Archive a : index.getArchives())
		{
			byte[] contents = a.decompress(storage.loadArchive(a));

			SpriteLoader loader = new SpriteLoader();
			SpriteDefinition[] sprites = loader.load(a.getArchiveId(), contents);

			for (SpriteDefinition sprite : sprites)
			{
				if (sprite.getHeight() <= 0 || sprite.getWidth() <= 0)
				{
					continue;
				}

				if (a.getNameHash() == mapsceneHash)
				{
					BufferedImage spriteImage = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
					spriteImage.setRGB(0, 0, sprite.getWidth(), sprite.getHeight(), sprite.getPixels(), 0, sprite.getWidth());

					// scale image down so it fits
					Image scaledImage = spriteImage.getScaledInstance(MAPICON_MAX_WIDTH, MAPICON_MAX_HEIGHT, 0);

					//assert scaledMapIcons.containsKey(sprite.getFrame()) == false;
					scaledMapIcons.put(sprite.getFrame(), scaledImage);
				}
			}
		}
	}

}