#extension GL_OES_EGL_image_external : require
#extension GL_OES_standard_derivatives : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

// https://www.shadertoy.com/view/Xtd3W7

void main() {
    vec4 color =  texture2D(uTexture, vTextureCoord);
    float gray = length(color.rgb);
    gl_FragColor = vec4(vec3(step(0.06, length(vec2(dFdx(gray), dFdy(gray))))), 1.0);
}