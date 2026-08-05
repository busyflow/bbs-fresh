#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float BoneIndex;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat3 NormalMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

/* One entry per bone of the model, in group index order, uploaded once per model instead of
 * once per bone. The whole model rides a single VAO whose vertices stay in their bone's local
 * space, and each vertex names its bone here — which is the entire point: a crowd of a
 * thirteen-bone villager costs one draw call each instead of thirteen. */
uniform mat4 BoneMats[128];

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;

void main()
{
    mat4 bone = BoneMats[int(BoneIndex)];
    vec3 position = (bone * vec4(Position, 1.0)).xyz;
    vec3 boneNormal = mat3(bone) * Normal;

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * position, FogShape);
    vec3 fixNormal = normalize(NormalMat * boneNormal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(boneNormal, 0.0);
}
