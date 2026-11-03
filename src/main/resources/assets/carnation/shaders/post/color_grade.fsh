#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    vec4 Tint;
    float Strength;
    float Saturation;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 graded = mix(vec3(luma), color.rgb, Saturation);
    graded = mix(graded, graded * Tint.rgb, Strength);
    fragColor = vec4(graded, color.a);
}
