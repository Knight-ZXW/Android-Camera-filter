precision mediump float; //指定默认精度

// MUST highp, otherwise will bad result on some device
varying highp vec2 vTextureCoord;
uniform sampler2D uTexture;

uniform float uAspectRatio;
uniform vec2 uCenter;
uniform float uRadius;
uniform float uScale;

void main() {
    float x = vTextureCoord.x;
    //float y = vTextureCoord.y * uAspectRatio + 0.5 - 0.5 * uAspectRatio;
    float y = (vTextureCoord.y - uCenter.y) * uAspectRatio + uCenter.y;
    vec2 textureCoordinateToUse = vec2(x, y);

    float dist = distance(uCenter, textureCoordinateToUse);
    textureCoordinateToUse = vTextureCoord;

    if (dist < uRadius) {
        textureCoordinateToUse -= uCenter;
        float percent = 1.0 - ((uRadius - dist) / uRadius) * uScale;
        percent = percent * percent;

        textureCoordinateToUse = textureCoordinateToUse * percent;
        textureCoordinateToUse += uCenter;
    }

    gl_FragColor = texture2D(uTexture, textureCoordinateToUse);
}