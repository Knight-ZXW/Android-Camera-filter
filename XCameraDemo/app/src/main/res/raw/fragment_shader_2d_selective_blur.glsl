precision mediump float;

varying highp vec2 vTextureCoord;

uniform sampler2D uTexture;
uniform sampler2D uTexture2;

uniform lowp float uExcludeCircleRadius;
uniform lowp vec2 uExcludeCirclePoint;
uniform lowp vec2 uExcludePoint1;
uniform lowp vec2 uExcludePoint2;
uniform lowp float uExcludeBlurSize;
uniform highp float uAspectRatio;
uniform int uBlurMode;

void main() {
    vec4 blurredImageColor = texture2D(uTexture, vTextureCoord);
    vec4 sharpImageColor = texture2D(uTexture2, vTextureCoord);

    vec2 textureCoordinateToUse = vTextureCoord;

 //Could not compile shader 35632:
 //E/GlUtil:  0:28: S0001: 'distance' is not a function
 //0:33: S0001: 'distance' is not a function

    float distance;
    if (uBlurMode == 0) {
        float x = vTextureCoord.x;
        float y = (vTextureCoord.y - uExcludeCirclePoint.y) * uAspectRatio + uExcludeCirclePoint.y;
        textureCoordinateToUse = vec2(x, y);
        // distance from center
        distance = distance(uExcludeCirclePoint, textureCoordinateToUse);
    }
    else {
        vec3 v1 = vec3(uExcludePoint2 - uExcludePoint1, 0.0);
        vec3 v2 = vec3(uExcludePoint1 - textureCoordinateToUse, 0.0);
        distance = length(cross(v1, v2)) / distance(uExcludePoint1, uExcludePoint2);
    }

    float a = smoothstep(
        uExcludeCircleRadius - uExcludeBlurSize,
        uExcludeCircleRadius,
        distance);

    gl_FragColor = mix(sharpImageColor, blurredImageColor, a);
}