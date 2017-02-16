#extension GL_OES_EGL_image_external : require
#extension GL_OES_standard_derivatives : require
//ERROR: 0:10: 'dFdx' : requires extension GL_OES_standard_derivatives to be enabled
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

void main() {
	vec3 col = texture2D(uTexture, vTextureCoord).rgb;
	float lum = col.x + col.y + col.z;
	vec2 deriv = vec2(dFdx(lum), dFdy(lum));
	float edge = sqrt(dot(deriv,deriv));
	if(edge > 0.032) discard;
	gl_FragColor = vec4(col,1.0);

    gl_FragColor = texture2D(uTexture, vTextureCoord);
}