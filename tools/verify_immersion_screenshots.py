#!/usr/bin/env python3
"""Check final compositor pixels, not only the client phase flags."""
from pathlib import Path
import sys
from PIL import Image, ImageChops

folder=Path(sys.argv[1])
with Image.open(folder/'rbd-return-silence.png') as image:
    assert image.convert('RGB').getextrema()[0][1]<=8 and image.convert('RGB').getextrema()[1][1]<=8 and image.convert('RGB').getextrema()[2][1]<=8, 'return silence must hide disconnection text and loading UI'
with Image.open(folder/'rbd-memory-silence.png') as image:
    central=image.convert('RGB').crop((0,0,image.width,int(image.height*0.88)))
    assert max(hi for lo,hi in central.getextrema())<=8,'memory silence must actually render black above the exit hint'
with Image.open(folder/'rbd-memory-ending.png') as normal, Image.open(folder/'rbd-memory-reduced.png') as reduced:
    assert normal.size==reduced.size
    assert ImageChops.difference(normal.convert('RGB'),reduced.convert('RGB')).getbbox() is not None,'full and reduced presentations must differ in rendered pixels'
print('Immersion compositor: return UI hidden; memory silence black; reduced presentation differs.')
