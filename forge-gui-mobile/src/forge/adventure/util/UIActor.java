package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.data.UIData;
import forge.adventure.scene.UIScene;
import forge.gui.GuiBase;
import forge.util.ShaderUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Group of controls that will be loaded from a configuration file
 */
public class UIActor extends Group {
    UIData data;
    Actor lastActor = null;
    public Array<UIScene.Selectable> selectActors = new Array<>();
    private HashMap<KeyBinding, Button> keyMap = new HashMap<>();
    public Array<KeyHintLabel> keyLabels = new Array<>();

    // Adventure UI uses its own Scene2D hierarchy instead of FScreen, so it must
    // apply iOS safe-area insets independently. Full-screen images are remembered
    // so they can remain edge-to-edge while controls are kept clear of the Dynamic
    // Island/notch and home indicator.
    private final ObjectMap<Actor, Rectangle> edgeToEdgeActors = new ObjectMap<>();
    private int safeScreenWidth = -1;
    private int safeScreenHeight = -1;
    private int safeLeft = -1;
    private int safeTop = -1;
    private int safeRight = -1;
    private int safeBottom = -1;

    public UIActor(FileHandle handle) {
        data = (new Json()).fromJson(UIData.class, handle);

        setWidth(data.width);
        setHeight(data.height);

        for (OrderedMap<String, String> element : data.elements) {
            String type = element.get("type");
            Actor newActor;
            if (type == null) {
                newActor = new Actor();
            } else {
                switch (type) {
                    case "Selector":
                        newActor = new Selector();
                        readSelectorProperties((Selector) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "Label":
                        newActor = Controls.newTextraLabel("");
                        readLabelProperties((TextraLabel) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "TypingLabel":
                        newActor = Controls.newTypingLabel("");
                        readLabelProperties((TextraLabel) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "Table":
                        newActor = new Table(Controls.getSkin());
                        readTableProperties((Table) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "Image":
                        newActor = new Image();
                        readImageProperties((Image) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "ImageButton":
                        newActor = new ImageButton(Controls.getSkin());
                        readImageButtonProperties((ImageButton) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "Window":
                        newActor = new Window("", Controls.getSkin());
                        readWindowProperties((Window) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "TextButton":
                        newActor = Controls.newTextButton("");
                        readButtonProperties((TextraButton) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "TextField":
                        newActor = new TextField("", Controls.getSkin());
                        readTextFieldProperties((TextField) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "Scroll":
                        newActor = new ScrollPane(null, Controls.getSkin());
                        readScrollPaneProperties((ScrollPane) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "CheckBox":
                        newActor = new CheckBox("", Controls.getSkin());
                        readCheckBoxProperties((CheckBox) newActor, new OrderedMap.OrderedMapEntries<>(element));
                        break;
                    case "SelectBox":
                        newActor = Controls.newComboBox();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + type);
                }
            }
            //load Actor Properties
            float yValue = 0;
            for (ObjectMap.Entry property : new OrderedMap.OrderedMapEntries<>(element)) {
                switch (property.key.toString()) {
                    case "selectable":
                        selectActors.add(new UIScene.Selectable(newActor));
                        break;
                    case "scale":
                        newActor.setScale((Float) property.value);
                        break;
                    case "width":
                        newActor.setWidth((Float) property.value);
                        break;
                    case "height":
                        newActor.setHeight((Float) property.value);
                        if (data.yDown)
                            newActor.setY(data.height - yValue - newActor.getHeight());
                        break;
                    case "x":
                        newActor.setX((Float) property.value);
                        break;
                    case "y":
                        yValue = (Float) property.value;
                        newActor.setY(data.yDown ? data.height - yValue - newActor.getHeight() : yValue);
                        break;
                    case "yOffset":
                        if (data.yDown) {
                            yValue = (Float) property.value + ((lastActor != null ? (data.height - lastActor.getY()) : 0f));
                            newActor.setY(data.height - yValue - newActor.getHeight());
                        } else {

                            yValue = (Float) property.value + ((lastActor != null ? (lastActor.getY()) : 0f));
                            newActor.setY(yValue);
                        }
                        break;
                    case "xOffset":
                        newActor.setX((Float) property.value + ((lastActor != null ? lastActor.getX() : 0f)));
                        break;
                    case "name":
                        newActor.setName((String) property.value);
                        break;
                }
            }
            lastActor = newActor;
            addActor(newActor);
        }

        rememberEdgeToEdgeActors();
        updateSafeAreaLayout();
    }

    private void rememberEdgeToEdgeActors() {
        final float tolerance = 1f;
        for (Actor actor : getChildren()) {
            if (actor instanceof Image
                    && actor.getX() <= tolerance
                    && actor.getY() <= tolerance
                    && actor.getWidth() >= data.width - tolerance
                    && actor.getHeight() >= data.height - tolerance) {
                edgeToEdgeActors.put(actor,
                        new Rectangle(actor.getX(), actor.getY(), actor.getWidth(), actor.getHeight()));
            }
        }
    }

    private void updateSafeAreaLayout() {
        if (Gdx.graphics == null) {
            return;
        }

        final int screenWidth = Gdx.graphics.getWidth();
        final int screenHeight = Gdx.graphics.getHeight();
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        if (GuiBase.isIOS() && !Forge.isTabletDevice) {
            left = Math.max(0, Gdx.graphics.getSafeInsetLeft());
            top = Math.max(0, Gdx.graphics.getSafeInsetTop());
            right = Math.max(0, Gdx.graphics.getSafeInsetRight());
            bottom = Math.max(0, Gdx.graphics.getSafeInsetBottom());
        }

        if (screenWidth == safeScreenWidth && screenHeight == safeScreenHeight
                && left == safeLeft && top == safeTop && right == safeRight && bottom == safeBottom) {
            return;
        }

        safeScreenWidth = screenWidth;
        safeScreenHeight = screenHeight;
        safeLeft = left;
        safeTop = top;
        safeRight = right;
        safeBottom = bottom;

        // Restore the untransformed root/background state before calculating a new layout.
        setPosition(0f, 0f);
        setScale(1f, 1f);
        for (ObjectMap.Entry<Actor, Rectangle> entry : edgeToEdgeActors) {
            Rectangle bounds = entry.value;
            entry.key.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        if (screenWidth <= 0 || screenHeight <= 0
                || (left == 0 && top == 0 && right == 0 && bottom == 0)) {
            return;
        }

        // The Adventure JSON uses a fixed virtual coordinate system. Convert the native
        // safe-area pixels into those virtual coordinates, then fit the root UI into it.
        final float worldWidth = data.width;
        final float worldHeight = data.height;
        final float insetLeft = left * worldWidth / screenWidth;
        final float insetRight = right * worldWidth / screenWidth;
        final float insetTop = top * worldHeight / screenHeight;
        final float insetBottom = bottom * worldHeight / screenHeight;
        final float usableWidth = Math.max(1f, worldWidth - insetLeft - insetRight);
        final float usableHeight = Math.max(1f, worldHeight - insetTop - insetBottom);
        final float scaleX = usableWidth / worldWidth;
        final float scaleY = usableHeight / worldHeight;

        setPosition(insetLeft, insetBottom);
        setScale(scaleX, scaleY);

        // Counter-transform true full-screen images so backgrounds still cover the entire
        // display. Everything else (buttons, labels, selectors, etc.) remains inside the
        // transformed safe-area root.
        for (ObjectMap.Entry<Actor, Rectangle> entry : edgeToEdgeActors) {
            Rectangle bounds = entry.value;
            entry.key.setBounds(
                    (bounds.x - insetLeft) / scaleX,
                    (bounds.y - insetBottom) / scaleY,
                    bounds.width / scaleX,
                    bounds.height / scaleY);
        }
    }

    @Override
    public void act(float delta) {
        updateSafeAreaLayout();
        super.act(delta);
    }

    public Button buttonPressed(int key) {
        for (Map.Entry<KeyBinding, Button> entry : keyMap.entrySet()) {
            if (entry.getKey().isPressed(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void readScrollPaneProperties(ScrollPane newActor, ObjectMap.Entries<String, String> entries) {
        newActor.setActor(Controls.newTextraLabel(""));
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "style":
                    newActor.setStyle(Controls.getSkin().get(property.value.toString(), ScrollPane.ScrollPaneStyle.class));
                    break;
            }
        }
    }

    private void readWindowProperties(Window newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "style":
                    newActor.setStyle(Controls.getSkin().get(property.value.toString(), Window.WindowStyle.class));
                    break;
            }
        }
        newActor.setMovable(false);
    }

    private void readTextFieldProperties(TextField newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "text":

                    newActor.setText(localize(property.value.toString()));
                    break;
                case "align":
                    newActor.setAlignment(((Float) property.value).intValue());
                    break;
            }
        }
    }

    public static String localize(String str) {
        Pattern regex = Pattern.compile("tr\\([^\\)]*\\)");
        for (int i = 0; i < 100; i++) {
            Matcher matcher = regex.matcher(str);
            if (!matcher.find())
                return str;
            str = matcher.replaceFirst(Forge.getLocalizer().getMessage(matcher.group().substring(3, matcher.group().length() - 1)));
        }
        return str;
    }

    private void readImageButtonProperties(ImageButton newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "style":
                    newActor.setStyle(Controls.getSkin().get(property.value.toString(), ImageButton.ImageButtonStyle.class));
                    break;
                case "binding":
                    keyMap.put(KeyBinding.valueOf(property.value.toString()), newActor);
                    break;
            }
        }
    }

    private void readLabelProperties(TextraLabel newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "text":
                    newActor.setText(localize(property.value.toString()));
                    break;
                case "font":
                case "fontName":
                    if (!property.value.toString().equals("default"))
                        newActor.setFont(Controls.getTextraFont(property.value.toString()));
                    break;
                case "style":
                    newActor.style = Controls.getLabelStyle(property.value.toString());
                    break;
                case "color":
                case "fontColor":
                    newActor.layout.setBaseColor(Controls.colorFromString(property.value.toString()));
                    break;
            }
        }
        newActor.setText(newActor.storedText);//necessary if color changes after text inserted
        newActor.layout();
    }

    private void readTableProperties(Table newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "font":
                    newActor.getSkin().get(Label.LabelStyle.class).font = Controls.getBitmapFont(property.value.toString());
                    if (property.value.toString().contains("black"))
                        newActor.getSkin().get(Label.LabelStyle.class).fontColor = Color.BLACK;
                    if (property.value.toString().contains("big"))
                        newActor.setScale(2, 2);
                    break;
            }
        }
    }

    private void readSelectorProperties(Selector newActor, ObjectMap.Entries<String, String> entries) {
    }

    private void readCheckBoxProperties(CheckBox newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "text":
                    newActor.setText(localize(property.value.toString()));
                    break;
            }
        }
    }

    private void readButtonProperties(TextraButton newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "text":
                    newActor.setText(localize(property.value.toString()));
                    break;
                case "style":
                    newActor.setStyle(Controls.getTextButtonStyle(property.value.toString()));
                    break;
                case "binding":
                    keyMap.put(KeyBinding.valueOf(property.value.toString()), newActor);
                    KeyHintLabel label = new KeyHintLabel(KeyBinding.valueOf(property.value.toString()));
                    keyLabels.add(label);
                    newActor.add(label);
                    break;
            }
        }
        newActor.layout();
    }

    private void readImageProperties(Image newActor, ObjectMap.Entries<String, String> entries) {
        for (ObjectMap.Entry property : entries) {
            switch (property.key.toString()) {
                case "image":
                    boolean is2D = property.value.toString().startsWith("ui");
                    Texture t = Forge.getAssets().getTexture(Config.instance().getFile(property.value.toString()), is2D, false);
                    TextureRegion tr = new TextureRegion(t);
                    if (property.value.toString().contains("title_bg")) {
                        ShaderDrawable shaderDrawable = new ShaderDrawable(ShaderUtil.getInstance().getShaderNightDay());
                        shaderDrawable.setCondition(() -> Config.instance().getSettingData().dayNightBG);
                        shaderDrawable.setUniformSetter(shader -> {
                            shader.setUniformf("u_timeOfDay", UIScene.getTimeOfDay());
                            shader.setUniformf("u_time", 0f);
                            shader.setUniformf("u_bias", 0.9f);
                        });
                        shaderDrawable.setRegion(tr);
                        newActor.setDrawable(shaderDrawable);
                    } else {
                        newActor.setDrawable(new TextureRegionDrawable(tr));
                    }
                    break;
            }
        }
    }

    public void onButtonPress(String name, Runnable func) {

        Actor button = findActor(name);
        if (button != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (button instanceof Button) {
                        if (((Button) button).isDisabled())
                            return;
                    }
                    func.run();
                }
            });
        }
    }

    public void controllerDisconnected() {
        for (KeyHintLabel label : keyLabels) {
            label.disconnected();
        }
    }

    public void controllerConnected() {
        for (KeyHintLabel label : keyLabels) {
            label.connected();
        }
    }

    public void pressUp(int code) {
        for (KeyHintLabel label : keyLabels) {
            label.buttonUp(code);
        }
    }

    public void pressDown(int code) {
        for (KeyHintLabel label : keyLabels) {
            label.buttonDown(code);
        }
    }


    private class KeyHintLabel extends TextraLabel {
        public KeyHintLabel(KeyBinding keyBinding) {
            super(keyBinding.getLabelText(false), Controls.getKeysFont());
            this.keyBinding = keyBinding;
        }

        KeyBinding keyBinding;

        public void connected() {
            updateText();
        }

        private void updateText() {
            setText(keyBinding.getLabelText(false));
            layout();
        }

        public void disconnected() {
            updateText();
        }

        public boolean buttonDown(int i) {
            if (keyBinding.isPressed(i))
                setText(keyBinding.getLabelText(true));
            layout();
            return false;
        }

        public boolean buttonUp(int i) {
            if (keyBinding.isPressed(i))
                updateText();
            return false;
        }
    }
}
