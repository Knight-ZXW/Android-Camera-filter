#extension GL_OES_EGL_image_external : require
precision mediump float;

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
    
    vec2 gradientDirection;
    gradientDirection.x = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;
    gradientDirection.y = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;

    float gradientMagnitude = length(gradientDirection);
    vec2 normalizedDirection = normalize(gradientDirection);
    normalizedDirection = sign(normalizedDirection) * floor(abs(normalizedDirection) + 0.617316); // Offset by 1-sin(pi/8) to set to 0 if near axis, 1 if away
    normalizedDirection = (normalizedDirection + 1.0) * 0.5; // Place -1.0 - 1.0 within 0 - 1.0

    gl_FragColor = vec4(gradientMagnitude, normalizedDirection.x, normalizedDirection.y, 1.0);
}
