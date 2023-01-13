#!/bin/bash

################
# Doogle Maps Tile Generator
# Copyright (C) 2019  Weird Gloop
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Author: Ralph Bisschops <ralph.bisschops.dev@gmail.com>
################

# Set folders
RESULT="./result/"
TEMP="./"

#######################
## Create New Images ##
#######################

# Combine image of layer 0 and lower (-1,-2,-3)
function createLayerSub0 {
  MAPXMIN=$1
  MAPYMIN=$2
  MAPZMIN=$3
  MAPXMAX=$4
  MAPYMAX=$5
  MAPZMAX=$6
  L=$7
  Q=$8
  mkdir -p "${RESULTMAP}${L}/"

  XMIN=$((MAPXMIN/Q))
  YMIN=$((MAPYMIN/Q))
  ZMIN=$MAPZMIN
  XMAX=$(((MAPXMAX-Q+1)/Q))
  YMAX=$(((MAPYMAX-Q+1)/Q))
  ZMAX=$MAPZMAX

  echo "Creating layer ${L}..."

  for Z in $(seq $ZMIN $ZMAX)
  do
    for X in $(seq $XMIN $XMAX)
    do
      for Y in $(seq $YMIN $YMAX)
      do
        imageList=""
        resultName="${RESULTMAP}${L}/${Z}_${X}_${Y}.png"
        X1=$((X*Q))
        Y1=$((Y*Q))
        X2=$((X*Q+(Q-1)))
        Y2=$((Y*Q+(Q-1)))

        drawImage=false


        for Yp in $(seq $Y2 -1 $Y1)
        do
          for Xp in $(seq $X1 $X2)
          do
            #imageList="${imageList} ${SOURCEMAP}0/${Z}_${Xp}_${Yp}.png"
            fileName=$(checkExistForMontage "${SOURCEMAP}0/${Z}_${Xp}_${Yp}.png")
            if [[ "$fileName" != "null:" ]]; then
              drawImage=true
            fi
            imageList="${imageList} $fileName"
          done
        done
        #echo "${imageList} -> ${resultName}"
        if $drawImage ; then
		  if [ $Z == 0 ]; then # if its a base floor, ignore alpha channel, save some data
			magick montage -tile ${Q}x${Q} -background black -alpha off -border 0 -geometry 64x64+0+0 $imageList $resultName
			magick convert $resultName -alpha off -resize 256x256 $resultName
		  else
		    magick montage -tile ${Q}x${Q} -background 'rgba(0,0,0,0)' -border 0 -geometry 64x64+0+0 $imageList $resultName
            magick convert $resultName -background 'rgba(0,0,0,0)' -resize 256x256 $resultName
		  fi
          printf "."
          
        fi
      done
    done
  done
  printf "\n"
}

# Combine image of layer 1
function createLayer1 {
  mkdir -p "${RESULTMAP}1/"
  MAPXMIN=$1
  MAPYMIN=$2
  MAPZMIN=$3
  MAPXMAX=$4
  MAPYMAX=$5
  MAPZMAX=$6

  XMIN=$((MAPXMIN/2))
  YMIN=$((MAPYMIN/2))
  ZMIN=$MAPZMIN
  XMAX=$((MAPXMAX/2))
  YMAX=$((MAPYMAX/2))
  ZMAX=$MAPZMAX

  echo "Creating layer 1..."

  for Z in $(seq $ZMIN $ZMAX)
  do
    for X in $(seq $XMIN $XMAX)
    do
      for Y in $(seq $YMIN $YMAX)
      do
        imageList=""
        X1=$((X*2))
        Y1=$((Y*2))
        X2=$((X*2+1))
        Y2=$((Y*2+1))

        drawImage=false

        for Yp in $(seq $Y2 -1 $Y1)
        do
          for Xp in $(seq $X1 $X2)
          do
            fileName=$(checkExistForMontage "${SOURCEMAP}1/${Z}_${Xp}_${Yp}.png")
            if [[ "$fileName" != "null:" ]]; then
              drawImage=true
            fi
            imageList="${imageList} $fileName"
          done
        done
        #echo "${imageList} -> ${RESULTMAP}1/${Z}_${X}_${Y}.png"
        if $drawImage ; then
          printf "."
		  
		  if [ $Z == 0 ]; then # if its a base floor, ignore alpha channel, save some data
			magick montage -tile 2x2 -background black -alpha off -border 0 -geometry 128x128+0+0 $imageList "${RESULTMAP}1/${Z}_${X}_${Y}.png"
		  else
		    magick montage -tile 2x2 -background 'rgba(0,0,0,0)' -border 0 -geometry 128x128+0+0 $imageList "${RESULTMAP}1/${Z}_${X}_${Y}.png"
		  fi
		  
          
        fi
      done
    done
  done
  printf "\n"
}

# Copy layer 2
# no changes needed
function createLayer2 {
  mkdir -p "${RESULTMAP}2/"
  MAPXMIN=$1
  MAPYMIN=$2
  MAPZMIN=$3
  MAPXMAX=$4
  MAPYMAX=$5
  MAPZMAX=$6

  XMIN=$MAPXMIN
  YMIN=$MAPYMIN
  ZMIN=$MAPZMIN
  XMAX=$MAPXMAX
  YMAX=$MAPYMAX
  ZMAX=$MAPZMAX

  echo "Creating layer 2..."

  for Z in $(seq $ZMIN $ZMAX)
  do
    for X in $(seq $XMIN $XMAX)
    do
      for Y in $(seq $YMIN $YMAX)
      do
        fileName="${SOURCEMAP}2/${Z}_${X}_${Y}.png"
        if [ -f $fileName ]; then
          printf "."
          cp "$fileName" "${RESULTMAP}2/${Z}_${X}_${Y}.png"
        fi
      done
    done
  done
  printf "\n"
}

# Cut image for layer 3
function createLayer3 {
  mkdir -p "${RESULTMAP}3/"
  MAPXMIN=$1
  MAPYMIN=$2
  MAPZMIN=$3
  MAPXMAX=$4
  MAPYMAX=$5
  MAPZMAX=$6

  XMIN=$((MAPXMIN*2))
  YMIN=$((MAPYMIN*2))
  ZMIN=$MAPZMIN
  XMAX=$((MAPXMAX*2))
  YMAX=$((MAPYMAX*2))
  ZMAX=$MAPZMAX

  # Divide in Q squares
  QMAX=4
  CUTXY=$((QMAX/2))

  echo "Creating layer 3..."

  for Z in $(seq $ZMIN $ZMAX)
  do
    for X in $(seq $XMIN $XMAX)
    do
      for Y in $(seq $YMIN $YMAX)
      do
        X1=$((X/CUTXY))
        Y1=$((Y/CUTXY))
        IMG="${SOURCEMAP}3/${Z}_${X1}_${Y1}.png"
        X2=$((X%CUTXY))
        Y2=$((Y%CUTXY))

        Q=$((X2 + (CUTXY-1-Y2) * CUTXY))
        # Q value part of image
        # 0 1
        # 2 3

        #echo "${IMG} Q:${Q} -> ${RESULTMAP}3/${Z}_${X}_${Y}.png"
        XOFFSET=$((X2*256))
        YOFFSET=$(((CUTXY-1-Y2)*256))

        if [ -f $IMG ]; then
          printf "."
          magick convert "${IMG}" -crop 256x256+${XOFFSET}+${YOFFSET} "${RESULTMAP}3/${Z}_${X}_${Y}.png"
        fi
      done
    done
  done
  printf "\n"
}

# Cut image for layer 4
function createLayer4 {
  mkdir -p "${RESULTMAP}4/"
  MAPXMIN=$1
  MAPYMIN=$2
  MAPZMIN=$3
  MAPXMAX=$4
  MAPYMAX=$5
  MAPZMAX=$6

  XMIN=$((MAPXMIN*4))
  YMIN=$((MAPYMIN*4))
  ZMIN=$MAPZMIN
  XMAX=$((MAPXMAX*4))
  YMAX=$((MAPYMAX*4))
  ZMAX=$MAPZMAX

  # Divide in Q squares
  QMAX=8
  CUTXY=$((QMAX/2))

  echo "Creating layer 4..."

  for Z in $(seq $ZMIN $ZMAX)
  do
    for X in $(seq $XMIN $XMAX)
    do
      for Y in $(seq $YMIN $YMAX)
      do
        X1=$((X/CUTXY))
        Y1=$((Y/CUTXY))
        IMG="${SOURCEMAP}4/${Z}_${X1}_${Y1}.png"
        X2=$((X%CUTXY))
        Y2=$((Y%CUTXY))

        Q=$((X2 + (CUTXY-1-Y2) * CUTXY))
        # Q value part of image
        #  0  1  2  3
        #  4  5  6  7
        #  8  9 10 11
        # 12 13 14 15

        # echo "${IMG} Q:${Q} -> ${RESULTMAP}4/${Z}_${X}_${Y}.png"

        XOFFSET=$((X2*256))
        YOFFSET=$(((CUTXY-1-Y2)*256))
        if [ -f $IMG ]; then
          printf "."
          magick convert "${IMG}" -crop 256x256+${XOFFSET}+${YOFFSET} "${RESULTMAP}4/${Z}_${X}_${Y}.png"
        fi
      done
    done
  done
  printf "\n"
}

# Check if image file exists and change name if it does not exist
function checkExistForMontage {
  NAME=$1

  if [ ! -f $NAME ]; then
      NAME="null:"
  fi
  echo $NAME
}

#########################

# Start timer
START=$(date +%s)

MAPXMIN=0
MAPYMIN=0
MAPZMIN=0

MAPXMAX=$((120-1)) #80 is normal but 120~ for region-3 is required
MAPYMAX=$((250-1)) #198 is normal but 250~ for region-3 is required
MAPZMAX=3

#MAPXMIN=40
#MAPYMIN=40
#MAPXMAX=$((60-1))
#MAPYMAX=$((60-1))

# remove all previous created images
#rm -R -d "${RESULT}"


RESULTMAP="${RESULT}/"
SOURCEMAP="${TEMP}/"

echo "-----MAP-----"

# Create layers:
createLayerSub0 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX "-3" 32
#createLayerSub0 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX "-2" 16
#createLayerSub0 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX "-1" 8
#createLayerSub0 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX "0" 4
#createLayer1 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX
#createLayer2 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX
#createLayer3 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX

#####createLayer4 $MAPXMIN $MAPYMIN $MAPZMIN $MAPXMAX $MAPYMAX $MAPZMAX



# End timer
END=$(date +%s)
DIFF=$(( $END - $START ))
echo "Execution time: $DIFF seconds"

# move images

echo "Updating data"

# Deploy new tiles
#rm -R -d /var/www/html/RSMap/tiles/*
#cp -R ./Maps/r/* /var/www/html/RSMap/tiles/

echo "All Done"
