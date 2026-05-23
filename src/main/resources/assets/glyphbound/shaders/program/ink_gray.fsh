#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform float GrayAmount;
uniform float InkAmount;

out vec4 fragColor;

void main() {
    vec4 diffuseColor = texture(DiffuseSampler, texCoord);
    float gray = dot(diffuseColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 grayscale = vec3(gray);
    vec3 inkTone = mix(grayscale, vec3(0.0, 0.0, 0.0), InkAmount);
    vec3 color = mix(diffuseColor.rgb, inkTone, GrayAmount);
    fragColor = vec4(color, diffuseColor.a);
}
