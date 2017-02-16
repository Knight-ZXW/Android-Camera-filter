#extension GL_OES_EGL_image_external : require
precision lowp float;

uniform samplerExternalOES uTexture;

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
    float bottomLeftIntensity = texture2D(uTexture, vBottomLeftTextureCoordinate).r;
    float topRightIntensity = texture2D(uTexture, vTopRightTextureCoordinate).r;
    float topLeftIntensity = texture2D(uTexture, vTopLeftTextureCoordinate).r;
    float bottomRightIntensity = texture2D(uTexture, vBottomRightTextureCoordinate).r;
    float leftIntensity = texture2D(uTexture, vLeftTextureCoordinate).r;
    float rightIntensity = texture2D(uTexture, vRightTextureCoordinate).r;
    float bottomIntensity = texture2D(uTexture, vBottomTextureCoordinate).r;
    float topIntensity = texture2D(uTexture, vTopTextureCoordinate).r;
    float centerIntensity = texture2D(uTexture, vTextureCoord).r;
    
    float pixelIntensitySum = bottomLeftIntensity + topRightIntensity + topLeftIntensity + bottomRightIntensity + leftIntensity + rightIntensity + bottomIntensity + topIntensity + centerIntensity;
    float sumTest = step(1.5, pixelIntensitySum);
    float pixelTest = step(0.01, centerIntensity);
    
    gl_FragColor = vec4(vec3(sumTest * pixelTest), 1.0);
}
