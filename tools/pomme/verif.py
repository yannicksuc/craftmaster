import sys, nbtlib, os
sch = nbtlib.load(sys.argv[1])['Schematic']
W,H,L = int(sch['Width']),int(sch['Height']),int(sch['Length'])
pal = {int(v):k for k,v in sch['Blocks']['Palette'].items()}
data = bytes(x & 0xff for x in sch['Blocks']['Data']); arr=[]; i=0
while i < len(data):
    v=0; s=0
    while True:
        b=data[i]; i+=1; v |= (b&0x7f)<<s; s+=7
        if not b&0x80: break
    arr.append(v)
out=["scoreboard players set #diff pomme_t 0"]
for y in range(H):
  for z in range(L):
    for x in range(W):
      b=pal[arr[x+z*W+y*W*L]]
      out.append("execute unless block ~%d ~%d ~%d %s run scoreboard players add #diff pomme_t 1" % (x,y,z,b))
p=sys.argv[2]; os.makedirs(os.path.dirname(p),exist_ok=True); open(p,"w",newline="\n").write("\n".join(out)+"\n")
