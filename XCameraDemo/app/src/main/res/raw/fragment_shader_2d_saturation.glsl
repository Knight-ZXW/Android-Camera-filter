precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

uniform lowp float uSaturation;
// Values from Graphics Shaders: Theory and Practice by Bailey and Cunningham
const mediump vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

void main() {
    lowp vec4 textureColor = texture2D(uTexture, vTextureCoord);
    lowp float luminance = dot(textureColor.rgb, luminanceWeighting);
    lowp vec3 greyScaleColor = vec3(luminance);

    gl_FragColor = vec4(
        mix(greyScaleColor, textureColor.rgb, uSaturation), textureColor.w);
}