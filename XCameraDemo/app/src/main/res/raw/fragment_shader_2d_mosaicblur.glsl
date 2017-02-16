precision mediump float;

varying vec2 vTextureCoord;

uniform sampler2D uTexture;

uniform vec2 uSamplerSteps;
uniform float uBlurPixels;

void main()
{
    vec2 coord = floor(vTextureCoord / uSamplerSteps / uBlurPixels) * uSamplerSteps * uBlurPixels;
    gl_FragColor = texture2D(uTexture, coord + uSamplerSteps * 0.5);
}