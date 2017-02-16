#extension GL_OES_standard_derivatives : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

// https://www.shadertoy.com/view/MscSzf
// Reference: https://www.shadertoy.com/view/MsSGD1
uniform float uiGlobalTime;// shader playback time (in seconds)

#define EdgeColor vec4(0.2, 0.2, 0.15, 1.0)
#define BackgroundColor vec4(1,0.95,0.85,1)
#define NoiseAmount 0.01
#define ErrorPeriod 30.0
#define ErrorRange 0.003

float triangle(float x)
{
	return abs(1.0 - mod(abs(x), 2.0)) * 2.0 - 1.0;
}

float rand(float x)
{
    return fract(sin(x) * 43758.5453);
}

void main() {
    float time = floor(uiGlobalTime * 16.0) / 16.0;
    vec2 uv = vTextureCoord.xy;
    uv += vec2(triangle(uv.y * rand(time) * 1.0) * rand(time * 1.9) * 0.005,
			triangle(uv.x * rand(time * 3.4) * 1.0) * rand(time * 2.1) * 0.005);

    float noise = (texture2D(uTexture, uv * 0.5).r - 0.5) * NoiseAmount;
    vec2 uvs[3];
    uvs[0] = uv + vec2(ErrorRange * sin(ErrorPeriod * uv.y + 0.0) + noise, ErrorRange * sin(ErrorPeriod * uv.x + 0.0) + noise);
    uvs[1] = uv + vec2(ErrorRange * sin(ErrorPeriod * uv.y + 1.047) + noise, ErrorRange * sin(ErrorPeriod * uv.x + 3.142) + noise);
    uvs[2] = uv + vec2(ErrorRange * sin(ErrorPeriod * uv.y + 2.094) + noise, ErrorRange * sin(ErrorPeriod * uv.x + 1.571) + noise);

    float edge = texture2D(uTexture, uvs[0]).r * texture2D(uTexture, uvs[1]).r * texture2D(uTexture, uvs[2]).r;
  	float diffuse = texture2D(uTexture, uv).g;

	float w = fwidth(diffuse) * 2.0;
	vec4 mCol = mix(BackgroundColor * 0.5, BackgroundColor, mix(0.0, 1.0, smoothstep(-w, w, diffuse - 0.3)));
	gl_FragColor = mix(EdgeColor, mCol, edge);
}