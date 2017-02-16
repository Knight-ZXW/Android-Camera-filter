#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

// https://www.shadertoy.com/view/4lXXDH
uniform vec3 uiResolution;
float c = 64.0;

void main() {
    vec2 pos = floor(vTextureCoord*c+.5)/c;
    gl_FragColor = texture2D(uTexture, pos);
}