#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

uniform lowp float uTemperature;
uniform lowp float uWhite;

const lowp vec3 whiteFilter = vec3(0.1, 0.1, 0.1);   //save for light whiting
const lowp vec3 warmFilter = vec3(0.0, 0.78, 0.92);

void main() {
    lowp vec4 source = texture2D(uTexture, vTextureCoord);
    highp float y = source.r * 0.299 + source.g * 0.587 + source.b * 0.114;
    highp float cr = (source.r - y)*0.713 + 0.5;
    highp float cb = (source.b - y)*0.564 + 0.5;

    highp float gray = y * 255.0;
    //gray = gray + (gray - 128.0)*cv + 0.5;
    gray = gray / (0.896865160897715 + 0.0032021590610318*gray - 0.0442923728433528*sqrt(gray));
    gray = gray<256.0? gray:255.0;
    y = gray / 255.0;

    highp vec3 rgb;
    rgb.r = y + 1.403*(cr - 0.5);
    rgb.g = y - 0.344*(cb - 0.5) - 0.714*(cr - 0.5);
    rgb.b = y + 1.773*(cb - 0.5);


    lowp vec3 whiteprocessed = vec3(
                                    (rgb.r < 0.5 ? (2.0 * rgb.r * whiteFilter.r) : (1.0 - 2.0 * (1.0 - rgb.r) * (1.0 - whiteFilter.r))), //adjusting temperature
                                    (rgb.g < 0.5 ? (2.0 * rgb.g * whiteFilter.g) : (1.0 - 2.0 * (1.0 - rgb.g) * (1.0 - whiteFilter.g))),
                                    (rgb.b < 0.5 ? (2.0 * rgb.b * whiteFilter.b) : (1.0 - 2.0 * (1.0 - rgb.b) * (1.0 - whiteFilter.b))));
//    lowp vec3 balancewhite = mix(rgb, whiteprocessed, -0.4756);
    lowp vec3 balancewhite = mix(rgb, whiteprocessed, uWhite);


    lowp vec3 temperprocessed = vec3(
                                     (balancewhite.r < 0.5 ? (2.0 * balancewhite.r * warmFilter.r) : (1.0 - 2.0 * (1.0 - balancewhite.r) * (1.0 - warmFilter.r))),
                                     (balancewhite.g < 0.5 ? (2.0 * balancewhite.g * warmFilter.g) : (1.0 - 2.0 * (1.0 - balancewhite.g) * (1.0 - warmFilter.g))),
                                     (balancewhite.b < 0.5 ? (2.0 * balancewhite.b * warmFilter.b) : (1.0 - 2.0 * (1.0 - balancewhite.b) * (1.0 - warmFilter.b))));
    lowp vec3 balanceresult = mix(balancewhite, temperprocessed, uTemperature);


    gl_FragColor = vec4(balanceresult, source.a);
}