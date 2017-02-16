precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

uniform lowp float uBrightness;

void main() {
    lowp vec4 textureColor = texture2D(uTexture, vTextureCoord);
    gl_FragColor = vec4((textureColor.rgb + vec3(uBrightness)), textureColor.w);
}