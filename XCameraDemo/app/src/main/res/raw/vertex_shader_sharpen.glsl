uniform mat4 uMVPMatrix;  // MVP 的变换矩阵（整体变形）
uniform mat4 uTexMatrix;  // Texture 的变换矩阵 （只对texture变形）

attribute vec4 aPosition;
attribute vec4 aTextureCoord;

varying vec2 vTextureCoord;

uniform float imageWidthFactor;
uniform float imageHeightFactor;
uniform float sharpness;

varying vec2 textureCoordinate;
varying vec2 leftTextureCoordinate;
varying vec2 rightTextureCoordinate;
varying vec2 topTextureCoordinate;
varying vec2 bottomTextureCoordinate;

varying float centerMultiplier;
varying float edgeMultiplier;

void main() {
    gl_Position = uMVPMatrix * aPosition;

    mediump vec2 widthStep = vec2(imageWidthFactor, 0.0);
    mediump vec2 heightStep = vec2(0.0, imageHeightFactor);

    vTextureCoord = (uTexMatrix * aTextureCoord).xy;
    leftTextureCoordinate = vTextureCoord.xy - widthStep;
    rightTextureCoordinate = vTextureCoord.xy + widthStep;
    topTextureCoordinate = vTextureCoord.xy + heightStep;
    bottomTextureCoordinate = vTextureCoord.xy - heightStep;

    centerMultiplier = 1.0 + 4.0 * sharpness;
    edgeMultiplier = sharpness;
}