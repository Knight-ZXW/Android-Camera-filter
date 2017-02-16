precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

varying vec2 vLeftTextureCoordinate;
varying vec2 vRightTextureCoordinate;

varying vec2 vTopTextureCoordinate;
varying vec2 vTopLeftTextureCoordinate;
varying vec2 vTopRightTextureCoordinate;

varying vec2 vBottomTextureCoordinate;
varying vec2 vBottomLeftTextureCoordinate;
varying vec2 vBottomRightTextureCoordinate;

uniform highp float uIntensity;
uniform highp float uThreshold;
uniform highp float uQuantizationLevels;

const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);

void main() {
    vec4 textureColor = texture2D(uTexture, vTextureCoord);

    float bottomLeftIntensity = texture2D(uTexture, vBottomLeftTextureCoordinate).r;
    float topRightIntensity = texture2D(uTexture, vTopRightTextureCoordinate).r;
    float topLeftIntensity = texture2D(uTexture, vTopLeftTextureCoordinate).r;
    float bottomRightIntensity = texture2D(uTexture, vBottomRightTextureCoordinate).r;
    float leftIntensity = texture2D(uTexture, vLeftTextureCoordinate).r;
    float rightIntensity = texture2D(uTexture, vRightTextureCoordinate).r;
    float bottomIntensity = texture2D(uTexture, vBottomTextureCoordinate).r;
    float topIntensity = texture2D(uTexture, vTopTextureCoordinate).r;

    float h = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;
    float v = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;

    float mag = length(vec2(h, v));

    vec3 posterizedImageColor = floor((textureColor.rgb * uQuantizationLevels) + 0.5) / uQuantizationLevels;

    float thresholdTest = 1.0 - step(uThreshold, mag);

    gl_FragColor = vec4(posterizedImageColor * thresholdTest, textureColor.a);
}