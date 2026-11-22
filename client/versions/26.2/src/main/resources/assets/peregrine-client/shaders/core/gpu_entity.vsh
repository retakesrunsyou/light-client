#version 330

// Light Client GPU mobs: each mob's shape lives on the graphics card in its own body-part space;
// this shader moves every corner by its part's matrix, read from a table that's refilled each
// frame. One draw covers every copy of a mob (gl_InstanceID picks the copy). The outputs match
// what Minecraft's entity fragment shader expects, so mobs look exactly the same.

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#moj_import <minecraft:light.glsl>
#endif
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;     // in the body part's own space
in vec2 UV0;
in ivec2 UV1;         // x: which body part
in vec3 Normal;       // in the body part's own space

// The table, in 16-byte rows of four 32-bit numbers. Per copy, starting at row
// ModelOffset.x + gl_InstanceID * ModelOffset.y: one row (light, overlay, colour ARGB, unused),
// then per body part six rows: 3 rows of its 4x4 matrix, then 3 rows of its normal matrix.
uniform usamplerBuffer PartData;

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

out vec2 texCoord0;

vec4 lc_row(int i) {
    return uintBitsToFloat(texelFetch(PartData, i));
}

void main() {
    int copy = int(ModelOffset.x + 0.5) + gl_InstanceID * int(ModelOffset.y + 0.5);
    uvec4 head = texelFetch(PartData, copy);
    uint light = head.x;
    uint overlay = head.y;
    uint argb = head.z;
    int m = copy + 1 + UV1.x * 6;

    vec4 p = vec4(Position, 1.0);
    vec3 pos = vec3(dot(lc_row(m), p), dot(lc_row(m + 1), p), dot(lc_row(m + 2), p));
    vec3 nrm = vec3(dot(lc_row(m + 3).xyz, Normal), dot(lc_row(m + 4).xyz, Normal), dot(lc_row(m + 5).xyz, Normal));
    float len = length(nrm);
    nrm = len > 0.0 ? nrm / len : vec3(0.0, 1.0, 0.0);
    vec4 color = vec4(float((argb >> 16u) & 255u), float((argb >> 8u) & 255u), float(argb & 255u), float(argb >> 24u)) / 255.0;
    ivec2 uvOverlay = ivec2(int(overlay & 65535u), int(overlay >> 16u));
    ivec2 uvLight = ivec2(int(light & 65535u), int(light >> 16u));

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

#ifdef PER_FACE_LIGHTING
    vec2 lit = minecraft_compute_light(Light0_Direction, Light1_Direction, nrm);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-lit, color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(lit, color);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = color;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, nrm, color);
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, uvLight);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, uvOverlay, 0);
#endif

    texCoord0 = UV0;
}
