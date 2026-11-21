#include <metal_stdlib>
using namespace metal;

// Limited cubic interpolation: four output luma samples per thread.
// Constrain the interpolated value to the central 2x2 range to suppress ringing.
static float4 cubic(float t) {
    float u=t*t, v=u*t;
    return float4(-.5f*t+u-.5f*v, 1.f-2.5f*u+1.5f*v, .5f*t+2.f*u-1.5f*v, -.5f*u+.5f*v);
}
kernel void spatial2x(texture2d<float,access::read> src [[texture(0)]],
                      texture2d<float,access::write> dst [[texture(1)]],
                      uint2 pos [[thread_position_in_grid]]) {
    int2 size(src.get_width(),src.get_height());
    if(any(pos>=uint2(size))) return;
    for(int j=0;j<2;++j) for(int i=0;i<2;++i) {
        int2 base=int2(pos)+int2(i-1,j-1);
        float4 wx=cubic(i==0?.75f:.25f), wy=cubic(j==0?.75f:.25f);
        float lo=1.f,hi=0.f,v=0.f;
        for(int y=0;y<4;++y) for(int x=0;x<4;++x) {
            float p=src.read(uint2(clamp(base+int2(x-1,y-1),int2(0),size-1))).r;
            v+=wx[x]*wy[y]*p;
            if(x>=1 && x<=2 && y>=1 && y<=2) { lo=min(lo,p); hi=max(hi,p); }
        }
        dst.write(float4(clamp(v,lo,hi)),pos*2+uint2(i,j));
    }
}
