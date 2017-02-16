#extension GL_OES_EGL_image_external : require
uniform samplerExternalOES uTexture;

varying highp vec2 vTextureCoord;
varying highp vec2 vLeftTextureCoordinate;
varying highp vec2 vRightTextureCoordinate;

varying highp vec2 vTopTextureCoordinate;
varying highp vec2 vTopLeftTextureCoordinate;
varying highp vec2 vTopRightTextureCoordinate;
varying highp vec2 vBottomTextureCoordinate;
varying highp vec2 vBottomLeftTextureCoordinate;
varying highp vec2 vBottomRightTextureCoordinate;

void main() {
    lowp float bottomColor = texture2D(uTexture, vBottomTextureCoordinate).r;
    lowp float bottomLeftColor = texture2D(uTexture, vBottomLeftTextureCoordinate).r;
    lowp float bottomRightColor = texture2D(uTexture, vBottomRightTextureCoordinate).r;
    lowp vec4 centerColor = texture2D(uTexture, vTextureCoord);
    lowp float leftColor = texture2D(uTexture, vLeftTextureCoordinate).r;
    lowp float rightColor = texture2D(uTexture, vRightTextureCoordinate).r;
    lowp float topColor = texture2D(uTexture, vTopTextureCoordinate).r;
    lowp float topRightColor = texture2D(uTexture, vTopRightTextureCoordinate).r;
    lowp float topLeftColor = texture2D(uTexture, vTopLeftTextureCoordinate).r;
    
    // Use a tiebreaker for pixels to the left and immediately above this one
    lowp float multiplier = 1.0 - step(centerColor.r, topColor);
    multiplier = multiplier * 1.0 - step(centerColor.r, topLeftColor);
    multiplier = multiplier * 1.0 - step(centerColor.r, leftColor);
    multiplier = multiplier * 1.0 - step(centerColor.r, bottomLeftColor);

    lowp float maxValue = max(centerColor.r, bottomColor);
    maxValue = max(maxValue, bottomRightColor);
    maxValue = max(maxValue, rightColor);
    maxValue = max(maxValue, topRightColor);

    gl_FragColor = vec4((centerColor.rgb * step(maxValue, centerColor.r) * multiplier), 1.0);
}
