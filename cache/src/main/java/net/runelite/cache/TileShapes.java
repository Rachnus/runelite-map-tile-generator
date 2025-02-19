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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TileShapes {

// TODO: Remove this old code if not needed anymore. Might not be needed anymore already.
//    private static int[][] tileShapes_2x2px = new int[][]{
//            {0,0,0,0},
//            {1,1,1,1},
//            {1,0,1,1},
//
//            {1,0,0,0},
//            {0,1,0,0},
//            {0,1,1,1},
//
//            {1,0,1,1},
//            {1,0,1,0},
//            {0,0,1,0},
//
//            {1,1,0,1},
//            {1,1,1,0},
//            {0,0,0,1},
//            {0,0,1,1},
//    };

//    private static int[][] tileRotation_2x2px = new int[][]{
//            {0,1,2,3},// 0 deg
//            {2,0,3,1},// 90 deg
//            {3,2,1,0},// 180 deg
//            {1,3,0,2},// 270 deg
//    };

//    private static int[][] tileShapes_4x4px = new int[][]{
//            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},// All grass
//            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},// All floor
//            {1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1},// floor diagonal bottom left
//
//            {1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},// floor near left half
//            {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1},// floor near right half
//            {0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},// grass top left
//
//            {1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1},// grass top right
//            {1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0},// floor left half
//            {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0},// floor left bottom
//
//            {1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1},// grass bottom left
//            {1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0},// curve floor top left
//            {0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1},// curve grass top left
//            {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1},// bump bottom
//    };

//    private static int[][] tileRotation_4x4px = new int[][]{
//            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},// No rotation
//            {12, 8, 4, 0, 13, 9, 5, 1, 14, 10, 6, 2, 15, 11, 7, 3},// 90 deg
//            {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0},// 180 deg
//            {3, 7, 11, 15, 2, 6, 10, 14, 1, 5, 9, 13, 0, 4, 8, 12},// 270 deg
//    };

    private static int[][] tileShapes_1x1px   = new int[13][1];
    private static int[][] tileShapes_2x2px   = new int[13][4];
    private static int[][] tileShapes_4x4px   = new int[13][16];
    private static int[][] tileShapes_8x8px   = new int[13][64];
    private static int[][] tileShapes_16x16px = new int[13][256];
    private static int[][] tileShapes_32x32px = new int[13][1024];

    private static int[][] tileRotation_1x1px   = new int[13][1];
    private static int[][] tileRotation_2x2px   = new int[13][4];
    private static int[][] tileRotation_4x4px   = new int[13][16];
    private static int[][] tileRotation_8x8px   = new int[13][64];
    private static int[][] tileRotation_16x16px = new int[13][256];
    private static int[][] tileRotation_32x32px = new int[13][1024];

    // If files ever get lost, they can be downloaded here:
    // https://drive.google.com/file/d/1OGdaEHhiHyPSkddLUaycB2dCHkqGdlKn/view?usp=sharing
    // They are black and white images, all 32x32 pixels with different shapes/corners.
    // TODO: Move files somewhere in repo
    private static String[] tileShapesFiles = new String[]{
            "./TileShapes/00.png",
            "./TileShapes/01.png",
            "./TileShapes/02.png",
            "./TileShapes/03.png",
            "./TileShapes/04.png",
            "./TileShapes/05.png",
            "./TileShapes/06.png",
            "./TileShapes/07.png",
            "./TileShapes/08.png",
            "./TileShapes/09.png",
            "./TileShapes/10.png",
            "./TileShapes/11.png",
            "./TileShapes/12.png",
    };

    public static void load(){
        // Create TileShapes and resizes
        BufferedImage img = null;
        try {
            for(int i = 0; i < tileShapesFiles.length; i++) {
                img = ImageIO.read(new File(tileShapesFiles[i]));

                tileShapes_32x32px[i] = convertToArray(img);
                tileShapes_16x16px[i] = convertToArray(resize(img, 16, 16));
                tileShapes_8x8px[i]   = convertToArray(resize(img, 8, 8));
                tileShapes_4x4px[i]   = convertToArray(resize(img, 4, 4));
                tileShapes_2x2px[i]   = convertToArray(resize(img, 2, 2));
                tileShapes_1x1px[i]   = convertToArray(resize(img, 1, 1));
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
//        for(int i = 0; i < 13; i++) {
//            System.out.println(Arrays.toString(tileShapes_2x2px[i]));
//        }
        //Create TileRotation matrices
        for(int degID = 0; degID < 4; degID++) {
            tileRotation_1x1px[degID]   = createRotationMatix(1, degID);
            tileRotation_2x2px[degID]   = createRotationMatix(2, degID);
            tileRotation_4x4px[degID]   = createRotationMatix(4, degID);
            tileRotation_8x8px[degID]   = createRotationMatix(8, degID);
            tileRotation_16x16px[degID] = createRotationMatix(16, degID);
            tileRotation_32x32px[degID] = createRotationMatix(32, degID);
        }

    }

    private static int[] createRotationMatix(int size, int degID){
        int[] result = new int[size*size];
        if(degID == 0){// No rotation
            for(int i = 0; i < size*size; i++) {
                result[i] = i;
            }
        }else if(degID == 1){// 90 deg
            int x,y;
            for(int i = 0; i < size*size; i++) {
                x = i % size;
                y = i / size;
                result[i] = (size-1-x)*size+y;
            }
        }else if(degID == 2){// 180 deg
            for(int i = 0; i < size*size; i++) {
                result[i] = size*size-1-i;
            }
        }else if(degID == 3){// 270 deg
            int x,y;
            for(int i = 0; i < size*size; i++) {
                x = i % size;
                y = i / size;
                result[i] = x*size+(size-1-y);
            }
        }
        return result;
    }

    private static BufferedImage resize(BufferedImage img, int height, int width) {
        Image tmp = img.getScaledInstance(width, height, Image.SCALE_FAST);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private static int[] convertToArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] result = new int[height*width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                // covert image to binary array
                // white = -1
                // black = -16777216
                result[row*width+col] = image.getRGB(col, row) < -8388608?0:1;
            }
        }

        return result;
    }

    public static int[] getTileShape(int tileSize, int shapeID){
        switch (tileSize){
            case 1:
                return tileShapes_1x1px[shapeID];
            case 2:
                return tileShapes_2x2px[shapeID];
            case 4:
                return tileShapes_4x4px[shapeID];
            case 8:
                return tileShapes_8x8px[shapeID];
            case 16:
                return tileShapes_16x16px[shapeID];
            case 32:
                return tileShapes_32x32px[shapeID];
        }
        return tileShapes_4x4px[shapeID];
    }

    public static int[] getTileRotation(int tileSize, int rotationID){
        switch (tileSize) {
            case 1:
                return tileRotation_1x1px[rotationID];
            case 2:
                return tileRotation_2x2px[rotationID];
            case 4:
                return tileRotation_4x4px[rotationID];
            case 8:
                return tileRotation_8x8px[rotationID];
            case 16:
                return tileRotation_16x16px[rotationID];
            case 32:
                return tileRotation_32x32px[rotationID];
        }
        return tileRotation_4x4px[rotationID];
    }
}