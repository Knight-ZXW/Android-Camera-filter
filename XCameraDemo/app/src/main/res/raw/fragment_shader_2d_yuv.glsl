precision mediump float;
varying vec2 vTextureCoord;

uniform sampler2D u_tex_y;
uniform sampler2D u_tex_u;
uniform sampler2D u_tex_v;

void main()
{
	/* method 1 good
	vec4 c = vec4((texture2D(u_tex_y, v_TexCoord).r - 16./255.) * 1.164);
	vec4 U = vec4(texture2D(u_tex_u, v_TexCoord).r - 128./255.);
	vec4 V = vec4(texture2D(u_tex_v, v_TexCoord).r - 128./255.);

	c += V * vec4(1.596, -0.813, 0, 0);
	c += U * vec4(0, -0.392, 2.017, 0);
	c.a = 1.0;

	gl_FragColor = c;*/

	/* method 2
	lowp vec3 rgb;
	mediump vec3 yuv;
	yuv.x = texture2D(u_tex_y, v_TexCoord).r;
	yuv.y = texture2D(u_tex_u, v_TexCoord).r - 0.5;
	yuv.z = texture2D(u_tex_v, v_TexCoord).r - 0.5;
	rgb = mat3(
	    1,   1,   1,
	    0,  -0.39465,2.03211,
	    1.13983,   -0.58060,  0) * yuv;
	gl_FragColor = vec4(rgb, 1);*/

	// CSC according to http://www.fourcc.org/fccyvrgb.php
	// from webrtc code
	float y = texture2D(u_tex_y, vTextureCoord).r;
	float u = texture2D(u_tex_u, vTextureCoord).r - 0.5;
	float v = texture2D(u_tex_v, vTextureCoord).r - 0.5;
	gl_FragColor = vec4(y + 1.403 * v,
        y - 0.344 * u - 0.714 * v,
        y + 1.77 * u, 1);
}