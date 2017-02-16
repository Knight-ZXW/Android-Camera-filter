#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

uniform vec2 uCenter;

void main() {
    vec2 normCoord = 2.0 * vTextureCoord - 1.0;
    vec2 normCenter = 2.0 * uCenter - 1.0;

    normCoord -= normCenter;
    vec2 s = sign(normCoord);
    normCoord = abs(normCoord);
    normCoord = 0.5 * normCoord + 0.5 * smoothstep(0.25, 0.5, normCoord) * normCoord;
    normCoord = s * normCoord;

    normCoord += normCenter;

    vec2 textureCoordinateToUse = normCoord / 2.0 + 0.5;

    gl_FragColor = texture2D(uTexture, textureCoordinateToUse );
}