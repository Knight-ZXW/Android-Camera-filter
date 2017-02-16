#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying highp vec2 vTextureCoord;
varying highp vec2 vExtraTextureCoord;

uniform samplerExternalOES uTexture;
uniform sampler2D uExtraTexture;

uniform float uThresholdSensitivity;
uniform float uSmoothing;
uniform vec3 uColorToReplace;

void main() {
    vec4 textureColor = texture2D(uTexture, vTextureCoord);

     float maskY = 0.2989 * uColorToReplace.r + 0.5866 * uColorToReplace.g + 0.1145 * uColorToReplace.b;
     float maskCr = 0.7132 * (uColorToReplace.r - maskY);
     float maskCb = 0.5647 * (uColorToReplace.b - maskY);

     float Y = 0.2989 * textureColor.r + 0.5866 * textureColor.g + 0.1145 * textureColor.b;
     float Cr = 0.7132 * (textureColor.r - Y);
     float Cb = 0.5647 * (textureColor.b - Y);

     //     float blendValue = 1.0 - smoothstep(thresholdSensitivity - smoothing, thresholdSensitivity , abs(Cr - maskCr) + abs(Cb - maskCb));
     float blendValue = smoothstep(
        uThresholdSensitivity,
        uThresholdSensitivity + uSmoothing,
        distance(vec2(Cr, Cb), vec2(maskCr, maskCb)));
     gl_FragColor = vec4(textureColor.rgb, textureColor.a * blendValue);
}