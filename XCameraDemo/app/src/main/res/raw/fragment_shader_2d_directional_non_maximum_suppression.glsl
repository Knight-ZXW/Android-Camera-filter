precision mediump float;

uniform sampler2D uTexture;

varying highp vec2 vTextureCoord;

uniform float uTexelWidth;
uniform float uTexelHeight;
uniform float uUpperThreshold;
uniform float uLowerThreshold;

void main() {
    vec3 currentGradientAndDirection = texture2D(uTexture, vTextureCoord).rgb;
    vec2 gradientDirection = ((currentGradientAndDirection.gb * 2.0) - 1.0) * vec2(uTexelWidth, uTexelHeight);

    float firstSampledGradientMagnitude = texture2D(uTexture, vTextureCoord + gradientDirection).r;
    float secondSampledGradientMagnitude = texture2D(uTexture, vTextureCoord - gradientDirection).r;

    float multiplier = step(firstSampledGradientMagnitude, currentGradientAndDirection.r);
    multiplier = multiplier * step(secondSampledGradientMagnitude, currentGradientAndDirection.r);

    float thresholdCompliance = smoothstep(uLowerThreshold, uUpperThreshold, currentGradientAndDirection.r);
    multiplier = multiplier * thresholdCompliance;

    gl_FragColor = vec4(multiplier, multiplier, multiplier, 1.0);
}
