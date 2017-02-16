uniform mat4 uMVPMatrix;  // MVP 的变换矩阵（整体变形）
uniform mat4 uTexMatrix;  // Texture 的变换矩阵 （只对texture变形）

attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform highp float uTexelWidth;
uniform highp float uTexelHeight;

varying vec2 vTextureCoord;
varying vec2 vLeftTextureCoordinate;
varying vec2 vRightTextureCoordinate;

varying vec2 vTopTextureCoordinate;
varying vec2 vTopLeftTextureCoordinate;
varying vec2 vTopRightTextureCoordinate;
varying vec2 vBottomTextureCoordinate;
varying vec2 vBottomLeftTextureCoordinate;
varying vec2 vBottomRightTextureCoordinate;

void main() {
    gl_Position = uMVPMatrix * aPosition;

    vec2 widthStep = vec2(uTexelWidth, 0.0);
    vec2 heightStep = vec2(0.0, uTexelHeight);
    vec2 widthHeightStep = vec2(uTexelWidth, uTexelHeight);
    vec2 widthNegativeHeightStep = vec2(uTexelWidth, -uTexelHeight);

    vTextureCoord = (uTexMatrix * aTextureCoord).xy;
    vLeftTextureCoordinate = vTextureCoord.xy - widthStep;
    vRightTextureCoordinate = vTextureCoord.xy + widthStep;
    vTopTextureCoordinate = vTextureCoord.xy - heightStep;
    vTopLeftTextureCoordinate = vTextureCoord.xy - widthHeightStep;
    vTopRightTextureCoordinate = vTextureCoord.xy + widthNegativeHeightStep;

    vBottomTextureCoordinate = vTextureCoord.xy + heightStep;
    vBottomLeftTextureCoordinate = vTextureCoord.xy - widthNegativeHeightStep;
    vBottomRightTextureCoordinate = vTextureCoord.xy + widthHeightStep;
}