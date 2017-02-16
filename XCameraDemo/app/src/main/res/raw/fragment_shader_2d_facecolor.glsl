precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

uniform float red_thres;
uniform float green_thres;
uniform float blue_thres;

void main() {
    vec4 origin = texture2D(uTexture, vTextureCoord);
    float r = origin.r;
    float g = origin.g;
    float b = origin.b;

    if (r > red_thres && g > green_thres && b > blue_thres
        && r > b && (max(max(r, g), b) - min(min(r, g), b)) > 0.0588 &&
        abs(r-g) > 0.0588) {
        gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    }
    else {
        gl_FragColor = origin;
    }
}