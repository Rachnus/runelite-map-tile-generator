/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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
package net.runelite.cache.definitions;

import java.util.List;
import lombok.Data;
import net.runelite.cache.region.Position;

@Data
public class WorldMapDefinition
{
	public String name;
	public int field450;
	public int defaultZoom;
	public int fileId;
	public int field453;
	public int field454;
	public int field456;
	public boolean isSurface;
	public List regionList;
	public String safeName;
	public Position position;
	public int field463;

	public boolean containsRegion(int x, int y, int z){
		boolean contained = false;
		for (int i = 0; i < regionList.size(); i++) {
			if (regionList.get(i) instanceof WorldMapType0){
				contained = contained || testContainsRegionType0((WorldMapType0)regionList.get(i), x, y, z);
			}else if (regionList.get(i) instanceof WorldMapType1){
				contained = contained || testContainsRegionType1((WorldMapType1)regionList.get(i), x, y, z);
			}else if (regionList.get(i) instanceof WorldMapType2){
				contained = contained || testContainsRegionType2((WorldMapType2)regionList.get(i), x, y, z);
			}else if (regionList.get(i) instanceof WorldMapType3){
				contained = contained || testContainsRegionType3((WorldMapType3)regionList.get(i), x, y, z);
			}
			// stop searching if found
			if(contained){
				break;
			}
		}
		return contained;
	}

	public Position getNewPositionOfRegion(int x, int y, int z){
		Position newPos = new Position(x,y,z);
		Position tempPos = null;
		for (int i = 0; i < regionList.size(); i++) {
			if (regionList.get(i) instanceof WorldMapType0){
				// Don't know if movement is allowed, not enough info
			}else if (regionList.get(i) instanceof WorldMapType1){
				// No movement allowed in this type
			}else if (regionList.get(i) instanceof WorldMapType2){
				// Don't know if movement is allowed
			}else if (regionList.get(i) instanceof WorldMapType3){
				tempPos = getNewPositionOfRegionType3((WorldMapType3)regionList.get(i), x, y, z);
			}

			if(tempPos != null){
				newPos = tempPos;
				break;
			}
		}
		//System.out.println("from: " + z + "," + x + "," + y + " to " +  newPos.getZ() + "," + newPos.getX() + "," + newPos.getY());
		return newPos;
	}

	public String getDefinitionBaseMap(){
		String output = "{\n" +
				"      \"mapId\": "+ this.fileId +",\n" +
				"      \"cacheVersion\": \"2019-03-28_1\",\n" +
				"      \"name\": \""+ this.name +"\",\n" +
				"      \"center\": ["+ this.position.getX() +", "+ this.position.getY() +"],\n" +
				// "      \"bounds\": [ [0, 0], [12800, 12800] ]\n" +
				"      \"bounds\": "+this.findBounds()+",\n" +
				"      \"zoomLimits\": [-3, 5],\n" +
				"      \"defaultZoom\": 1,\n" +
				"      \"maxNativeZoom\": 3\n" +
				"    },";
		//System.out.println("from: " + z + "," + x + "," + y + " to " +  newPos.getZ() + "," + newPos.getX() + "," + newPos.getY());
		return output;
	}

	private String findBounds(){

		// bounds: (xl,yd,xr,yu)
		int[] bounds = {
				this.position.getX()/64,
				this.position.getY()/64,
				this.position.getX()/64,
				this.position.getY()/64
		};

		for (int i = 0; i < regionList.size(); i++) {
			if (regionList.get(i) instanceof WorldMapType0){
				bounds = moveBoundsRegionType0((WorldMapType0)regionList.get(i), bounds);
			}else if (regionList.get(i) instanceof WorldMapType1){
				bounds = moveBoundsRegionType1((WorldMapType1)regionList.get(i), bounds);
			}else if (regionList.get(i) instanceof WorldMapType2){
				bounds = moveBoundsRegionType2((WorldMapType2)regionList.get(i), bounds);
			}else if (regionList.get(i) instanceof WorldMapType3){
				bounds = moveBoundsRegionType3((WorldMapType3)regionList.get(i), bounds);
			}
		}
		int margin = 100;

		return "[ ["+(bounds[0]*64-margin)+", "+(bounds[1]*64-margin)+"], ["+(bounds[2]*64+margin)+", "+(bounds[3]*64+margin)+"] ]";
	}

	private int[] moveBoundsRegionType0(WorldMapType0 wmt, int[] bounds){
		if(bounds[0] > wmt.xLow){
			bounds[0] = wmt.xLow;
		}
		if(bounds[1] > wmt.yLow){
			bounds[1] = wmt.yLow;
		}
		if(bounds[2] < wmt.xLow){
			bounds[2] = wmt.xLow;
		}
		if(bounds[3] < wmt.yLow){
			bounds[3] = wmt.yLow;
		}
		return bounds;
	}

	private int[] moveBoundsRegionType1(WorldMapType1 wmt, int[] bounds){
		// Only using lowerLeft and upperRight coordinates for test.
		// Other coordinates are unnecessary

		if(bounds[0] > wmt.xLowerLeft){
			bounds[0] = wmt.xLowerLeft;
		}
		if(bounds[1] > wmt.yLowerLeft){
			bounds[1] = wmt.yLowerLeft;
		}
		if(bounds[2] < wmt.xUpperRight){
			bounds[2] = wmt.xUpperRight;
		}
		if(bounds[3] < wmt.yUpperRight){
			bounds[3] = wmt.yUpperRight;
		}
		return bounds;
	}

	private int[] moveBoundsRegionType2(WorldMapType2 wmt, int[] bounds){
		if(bounds[0] > wmt.xLow){
			bounds[0] = wmt.xLow;
		}
		if(bounds[1] > wmt.yLow){
			bounds[1] = wmt.yLow;
		}
		if(bounds[2] < wmt.xHigh){
			bounds[2] = wmt.xHigh;
		}
		if(bounds[3] < wmt.yHigh){
			bounds[3] = wmt.yHigh;
		}
		return bounds;
	}

	private int[] moveBoundsRegionType3(WorldMapType3 wmt, int[] bounds){
		if(bounds[0] > wmt.newX) {
			bounds[0] = wmt.newX;
		}
		if(bounds[1] > wmt.newY){
			bounds[1] = wmt.newY;
		}
		if(bounds[2] < wmt.newX){
			bounds[2] = wmt.newX;
		}
		if(bounds[3] < wmt.newY){
			bounds[3] = wmt.newY;
		}
		return bounds;
	}

	private Position getNewPositionOfRegionType3(WorldMapType3 wmt, int x, int y, int z){
		Position newPos = null;
		if(x == wmt.oldX && y == wmt.oldY && z == wmt.oldPlane) // might be wrong
		{
			newPos = new Position(wmt.newX, wmt.newY, 0);
			//System.out.println("from: " + z + "," + x + "," + y + " to " +  newPos.getZ() + "," + newPos.getX() + "," + newPos.getY());
		}
		return newPos;
	}

	private boolean testContainsRegionType0(WorldMapType0 wmt, int x, int y, int z)
	{

		// z coordinates might be wrong, not enough info, might be field606, field607 or field609
		if(x == wmt.xLow && y == wmt.yLow && z == wmt.plane)
		{
			return true;
		}
		return false;
	}

	private boolean testContainsRegionType1(WorldMapType1 wmt, int x, int y, int z)
	{

		// Only using lowerLeft and upperRight coordinates for test.
		// Other coordinates are unnecessary

		if(x >= wmt.xLowerLeft && x <= wmt.xUpperRight && y >= wmt.yLowerLeft && y <= wmt.yUpperRight) //might b wrong
		{
			return true;
		}
		return false;
	}

	private boolean testContainsRegionType2(WorldMapType2 wmt, int x, int y, int z)
	{
		if(x == wmt.xLow && y == wmt.yLow && z == wmt.plane){
			return true;
		}
		return false;
	}

	private boolean testContainsRegionType3(WorldMapType3 wmt, int x, int y, int z)
	{
		if(x == wmt.oldX && y == wmt.oldY /*&& z == wmt.field377*/){
			return true;
		}
		return false;
	}
}
