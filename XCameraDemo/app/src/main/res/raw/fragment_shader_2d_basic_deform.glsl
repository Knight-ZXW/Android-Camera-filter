precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

// https://www.shadertoy.com/view/XdsGzH
uniform float uiGlobalTime;

void main() {
    float stongth = 0.3;
	float waveu = sin((vTextureCoord.y + uiGlobalTime) * 20.0) * 0.5 * 0.05 * stongth;
	gl_FragColor = texture2D(uTexture, vTextureCoord + vec2(waveu, 0));
}