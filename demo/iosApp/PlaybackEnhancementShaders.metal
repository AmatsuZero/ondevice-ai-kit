#include <metal_stdlib>
using namespace metal;
struct PlaybackVertex { float4 position [[position]]; float2 uv; };
vertex PlaybackVertex playbackVertex(uint id [[vertex_id]]) { const float2 p[]={{-1,-1},{1,-1},{-1,1},{1,1}}; const float2 u[]={{0,1},{1,1},{0,0},{1,0}}; return {float4(p[id],0,1),u[id]}; }
fragment float4 playbackFragment(PlaybackVertex in [[stage_in]], texture2d<float> y [[texture(0)]], texture2d<float> uv [[texture(1)]]) {
 constexpr sampler s(filter::linear,address::clamp_to_edge); float l=y.sample(s,in.uv).r; float2 c=uv.sample(s,in.uv).rg-float2(.5); return float4(l+1.5748*c.y,l-.187324*c.x-.468124*c.y,l+1.8556*c.x,1);
}
