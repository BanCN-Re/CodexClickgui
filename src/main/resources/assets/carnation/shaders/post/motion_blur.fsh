#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MotionBlurConfig {
    vec2 Direction;
    float Radius;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 delta = Direction / InSize;
    vec4 base = texture(InSampler, texCoord);

    vec4 sum = vec4(0.0);
    float weightSum = 0.0;
    for (int i = -3; i <= 3; i++) {
        float weight = 1.0 - abs(float(i)) / 4.0;
        sum += texture(InSampler, texCoord + delta * float(i) * Radius) * weight;
        weightSum += weight;
    }
    fragColor = mix(base, sum / weightSum, 0.55);
}
