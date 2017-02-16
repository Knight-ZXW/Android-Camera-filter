#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

// https://www.shadertoy.com/view/ll33R7
uniform float uiGlobalTime;

void main() {
    vec2 uv = vTextureCoord;
	vec4 ogColor = texture2D(uTexture, vTextureCoord);
    vec4 texColor = ogColor;

    if(texColor.r + texColor.g + texColor.b >= 1.0)
    {
		texColor = vec4(1,1,1,1);
    }
    else
    {
        texColor = vec4(0,0,0,1);
    }
	texColor.b = (uv.x+uv.y)/2.0;


    float multiplier = abs(sin(uiGlobalTime));
    vec4 blend = (((multiplier)*(texColor)) + (.5*(1.0-multiplier)*(ogColor)))/2.0;

    gl_FragColor = blend;
}