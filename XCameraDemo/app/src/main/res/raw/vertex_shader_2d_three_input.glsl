uniform mat4 uMVPMatrix;
uniform mat4 uTexMatrix;

attribute vec4 aPosition;

attribute vec4 aTextureCoord;
attribute vec4 aTextureCoord2;
attribute vec4 aTextureCoord3;

varying vec2 vTextureCoord;
varying vec2 vTextureCoord2;
varying vec2 vTextureCoord3;

void main() {
    gl_Position     = uMVPMatrix * aPosition;
    vTextureCoord   = aTextureCoord.xy;
    vTextureCoord2  = aTextureCoord2.xy;
    vTextureCoord3  = aTextureCoord3.xy;
}