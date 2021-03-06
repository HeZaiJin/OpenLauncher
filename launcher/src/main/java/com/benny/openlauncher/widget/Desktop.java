package com.benny.openlauncher.widget;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import com.benny.openlauncher.activity.Home;
import com.benny.openlauncher.R;
import com.benny.openlauncher.util.AppManager;
import com.benny.openlauncher.util.DesktopCallBack;
import com.benny.openlauncher.util.DragAction;
import com.benny.openlauncher.util.ItemViewFactory;
import com.benny.openlauncher.util.LauncherAction;
import com.benny.openlauncher.util.LauncherSettings;
import com.benny.openlauncher.util.Tool;
import com.bennyv5.smoothviewpager.SmoothPagerAdapter;
import com.bennyv5.smoothviewpager.SmoothViewPager;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import in.championswimmer.sfg.lib.SimpleFingerGestures;

public class Desktop extends SmoothViewPager implements OnDragListener ,DesktopCallBack {
    public int pageCount;

    public List<CellContainer> pages = new ArrayList<>();

    public OnDesktopEditListener listener;

    public boolean inEditMode;

    public View previousItemView;
    public Item previousItem;
    public int previousPage = -1;

    private Home home;

    private PagerIndicator pageIndicator;

    public void setPageIndicator(PagerIndicator pageIndicator) {
        this.pageIndicator = pageIndicator;
    }

    public Desktop(Context c, AttributeSet attr) {
        super(c, attr);
        init(c);
    }

    public Desktop(Context c) {
        super(c);
        init(c);
    }

    private void init(Context c) {
        if (isInEditMode()) return;

        pageCount = LauncherSettings.getInstance(c).generalSettings.desktopPageCount;
        setAdapter(new Adapter());
        setOnDragListener(this);

        setCurrentItem(LauncherSettings.getInstance(c).generalSettings.desktopHomePage);
    }

    public void initDesktopItem(Home home) {
        this.home = home;
        for (int i = 0; i < LauncherSettings.getInstance(getContext()).desktopData.size(); i++) {
            if (pages.size() <= i) break;
            pages.get(i).removeAllViews();
            for (int j = 0; j < LauncherSettings.getInstance(getContext()).desktopData.get(i).size(); j++) {
                addItemToPagePosition(LauncherSettings.getInstance(getContext()).desktopData.get(i).get(j), i);
            }
        }
    }

    public void addPageRight() {
        addPageRight(false);
    }

    public void addPageRight(boolean hideGrid) {
        LauncherSettings.getInstance(getContext()).desktopData.add(new ArrayList<Item>());
        LauncherSettings.getInstance(getContext()).generalSettings.desktopPageCount++;
        pageCount++;

        final int previousPage = getCurrentItem();
        ((Adapter) getAdapter()).addPageRight();
        setCurrentItem(previousPage + 1);

        for (CellContainer cellContainer : pages)
            cellContainer.setHideGrid(false);

        pageIndicator.invalidate();
    }

    public void addPageLeft() {
        LauncherSettings.getInstance(getContext()).desktopData.add(getCurrentItem(), new ArrayList<Item>());
        LauncherSettings.getInstance(getContext()).generalSettings.desktopPageCount++;
        pageCount++;

        final int previousPage = getCurrentItem();
        ((Adapter) getAdapter()).addPageLeft();
        setCurrentItem(previousPage - 1);

        for (CellContainer cellContainer : pages)
            cellContainer.setHideGrid(false);

        pageIndicator.invalidate();
    }

    public void removeCurrentPage() {
        if (pageCount == 1) return;
        LauncherSettings.getInstance(getContext()).desktopData.remove(getCurrentItem());
        LauncherSettings.getInstance(getContext()).generalSettings.desktopPageCount--;
        pageCount--;

        int previousPage = getCurrentItem();
        ((Adapter) getAdapter()).removePage(getCurrentItem());

        for (CellContainer v : pages) {
            v.setAlpha(0);
            v.animate().alpha(1);
            v.setScaleX(0.7f);
            v.setScaleY(0.7f);
            v.animateBackgroundShow();
        }
        setCurrentItem(previousPage);

        pageIndicator.invalidate();
    }

    @Override
    public boolean onDrag(View p1, DragEvent p2) {
        switch (p2.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                Tool.print("ACTION_DRAG_STARTED");
                switch (((DragAction) p2.getLocalState()).action) {
                    case ACTION_APP:
                    case ACTION_GROUP:
                    case ACTION_APP_DRAWER:
                    case ACTION_WIDGET:
                    case ACTION_SHORTCUT:
                        return true;
                }
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                Tool.print("ACTION_DRAG_ENTERED");
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                Tool.print("ACTION_DRAG_EXITED");
                return true;

            case DragEvent.ACTION_DROP:
                Tool.print("ACTION_DROP");
                Intent intent = p2.getClipData().getItemAt(0).getIntent();
                intent.setExtrasClassLoader(Item.class.getClassLoader());
                Item item = intent.getParcelableExtra("mDragData");
                if (item.type == Item.Type.WIDGET) {
                    if (addItemToPosition(item, (int) p2.getX(), (int) p2.getY())) {
                        home.desktop.consumeRevert();
                        home.dock.consumeRevert();
                    } else {
                        Toast.makeText(getContext(), R.string.toast_notenoughspace, Toast.LENGTH_SHORT).show();
                        home.dock.revertLastItem();
                        home.desktop.revertLastItem();
                    }
                }
                if (item.type == Desktop.Item.Type.APP || item.type == Item.Type.GROUP || item.type == Item.Type.SHORTCUT) {
                    if (addItemToPosition(item, (int) p2.getX(), (int) p2.getY())) {
                        home.desktop.consumeRevert();
                        home.dock.consumeRevert();
                    } else {
                        Point pos = pages.get(getCurrentItem()).touchPosToCoordinate((int) p2.getX(), (int) p2.getY(), item.spanX, item.spanY,false);
                        View itemView = pages.get(getCurrentItem()).coordinateToChildView(pos);

                        if (itemView != null)
                            if (Desktop.handleOnDropOver(home,item, (Item) itemView.getTag(),itemView,pages.get(getCurrentItem()),getCurrentItem(),this)){
                                home.desktop.consumeRevert();
                                home.dock.consumeRevert();
                            }else {
                                Toast.makeText(getContext(), R.string.toast_notenoughspace, Toast.LENGTH_SHORT).show();
                                home.dock.revertLastItem();
                                home.desktop.revertLastItem();
                            }
                        else {
                            Toast.makeText(getContext(), R.string.toast_notenoughspace, Toast.LENGTH_SHORT).show();
                            home.dock.revertLastItem();
                            home.desktop.revertLastItem();
                        }
                    }
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                Tool.print("ACTION_DRAG_ENDED");
                return true;
        }
        return false;
    }

    /**
     * @param args array storing the item(pos 0) and view ref(pos 1).
     */
    @Override
    public void setLastItem(Object... args){
        View v = (View) args[1];
        Desktop.Item item = (Desktop.Item) args[0];

        //Remove the item from settings
        removeItemFromSettings(item);
        //end

        previousPage = getCurrentItem();
        previousItemView = v;
        previousItem = item;
        pages.get(getCurrentItem()).removeView(v);
    }

    @Override
    public void consumeRevert() {
        previousItem = null;
        previousItemView = null;
        previousPage = -1;
    }

    @Override
    public void revertLastItem() {
        if (previousItemView != null && getAdapter().getCount() >= previousPage && previousPage > -1) {
            pages.get(getCurrentItem()).addViewToGrid(previousItemView);

            if (LauncherSettings.getInstance(getContext()).desktopData.size() < getCurrentItem() + 1)
                LauncherSettings.getInstance(getContext()).desktopData.add(previousPage, new ArrayList<Item>());
            LauncherSettings.getInstance(getContext()).desktopData.get(previousPage).add(previousItem);

            previousItem = null;
            previousItemView = null;
            previousPage = -1;
        }
    }

    @Override
    public void addItemToPagePosition(final Item item, int page) {
        View itemView = ItemViewFactory.getItemView(getContext(),this,item,ItemViewFactory.NO_FLAGS);

        if (itemView == null) {
            LauncherSettings.getInstance(getContext()).desktopData.get(page).remove(item);
        } else
            pages.get(page).addViewToGrid(itemView, item.x, item.y, item.spanX, item.spanY);
    }

    @Override
    public boolean addItemToPosition(final Item item, int x, int y) {
        CellContainer.LayoutParams positionToLayoutPrams = pages.get(getCurrentItem()).positionToLayoutPrams(x, y, item.spanX, item.spanY);
        if (positionToLayoutPrams != null) {
            //Add the item to settings
            item.x = positionToLayoutPrams.x;
            item.y = positionToLayoutPrams.y;
            if (LauncherSettings.getInstance(getContext()).desktopData.size() < getCurrentItem() + 1)
                LauncherSettings.getInstance(getContext()).desktopData.add(getCurrentItem(), new ArrayList<Item>());
            LauncherSettings.getInstance(getContext()).desktopData.get(getCurrentItem()).add(item);
            //end

            View itemView = ItemViewFactory.getItemView(getContext(),this,item,ItemViewFactory.NO_FLAGS);

            if (itemView != null) {
                itemView.setLayoutParams(positionToLayoutPrams);
                pages.get(getCurrentItem()).addView(itemView);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onPageScrolled(int position, float offset, int offsetPixels) {
        if (isInEditMode()) return;

        WallpaperManager.getInstance(getContext()).setWallpaperOffsets(getWindowToken(), (float) (position + offset) / (pageCount - 1), 0);
        super.onPageScrolled(position, offset, offsetPixels);
    }

    @Override
    public void removeItemFromSettings(Item item) {
        LauncherSettings.getInstance(getContext()).desktopData.get(getCurrentItem()).remove(item);
    }

    @Override
    public void addItemToSettings(Item item) {
        LauncherSettings.getInstance(getContext()).desktopData.get(getCurrentItem()).add(item);
    }

    public class Adapter extends SmoothPagerAdapter {

        float scaleFactor = 1f;

        private MotionEvent currentEvent;

        public Adapter() {
            pages = new ArrayList<>();
            for (int i = 0; i < getCount(); i++) {
                pages.add(getItemLayout());
            }
        }

        public void addPageLeft() {
            pages.add(0, getItemLayout());
            notifyDataSetChanged();
        }

        public void addPageRight() {
            pages.add(getItemLayout());
            notifyDataSetChanged();
        }

        public void removePage(int position) {
            pages.remove(position);
            notifyDataSetChanged();
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return pageCount;
        }

        @Override
        public boolean isViewFromObject(View p1, Object p2) {
            return p1 == p2;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int pos) {
            ViewGroup layout = pages.get(pos);
            container.addView(layout);
            return layout;
        }

        private SimpleFingerGestures.OnFingerGestureListener getGestureListener(){
            return new SimpleFingerGestures.OnFingerGestureListener() {
                @Override
                public boolean onSwipeUp(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onSwipeDown(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onSwipeLeft(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onSwipeRight(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onPinch(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onUnpinch(int i, long l, double v) {
                    return false;
                }

                @Override
                public boolean onDoubleTap(int i) {
                    LauncherAction.RunAction(LauncherAction.Action.LockScreen,getContext(), null);
                    return true;
                }
            };
        }

        private CellContainer getItemLayout() {
            CellContainer layout = new CellContainer(getContext());

            layout.setSoundEffectsEnabled(false);
            SimpleFingerGestures mySfg = new SimpleFingerGestures();
            mySfg.setOnFingerGestureListener(getGestureListener());
            layout.gestures = mySfg;
            layout.setGridSize(LauncherSettings.getInstance(getContext()).generalSettings.desktopGridx, LauncherSettings.getInstance(getContext()).generalSettings.desktopGridy);
            layout.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    currentEvent = motionEvent;
                    return false;
                }
            });
            layout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    scaleFactor = 1f;
                    for (final CellContainer v : pages) {
                        v.blockTouch = false;
                        v.animateBackgroundHide();
                        v.animate().scaleX(scaleFactor).scaleY(scaleFactor).setInterpolator(new AccelerateDecelerateInterpolator());
                    }
                    if (!inEditMode)
                        if (currentEvent != null)
                            WallpaperManager.getInstance(view.getContext()).sendWallpaperCommand(view.getWindowToken(), WallpaperManager.COMMAND_TAP, (int) currentEvent.getX(), (int) currentEvent.getY(), 0, null);

                    inEditMode = false;
                    if (listener != null)
                        listener.onFinished();
                }
            });
            layout.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    scaleFactor = 0.8f;
                    for (CellContainer v : pages) {
                        v.setPivotY(v.getHeight()/2 -Tool.dp2px(50,getContext()));
                        v.setPivotX(v.getWidth()/2);

                        v.blockTouch = true;
                        v.animateBackgroundShow();
                        v.animate().scaleX(scaleFactor).scaleY(scaleFactor).setInterpolator(new AccelerateDecelerateInterpolator());
                    }
                    inEditMode = true;
                    if (listener != null)
                        listener.onStart();
                    return true;
                }
            });

            return layout;
        }
    }

    public static boolean handleOnDropOver(Home home,Item dropItem,Item item,View itemView,ViewGroup parent,int page,DesktopCallBack callBack){
        if (item == null){
            return false;
        }
        switch (item.type){
            case APP:
            case SHORTCUT:
            case GROUP:
                if (dropItem.type == Desktop.Item.Type.APP || dropItem.type == Desktop.Item.Type.SHORTCUT && item.actions.length < GroupPopupView.GroupDef.maxItem) {
                    callBack.removeItemFromSettings(item);
                    parent.removeView(itemView);

                    item.addActions(dropItem.actions[0]);
                    if (item.name == null || item.name.isEmpty())
                        item.name = "Unnamed";
                    item.type = Desktop.Item.Type.GROUP;
                    callBack.addItemToSettings(item);
                    callBack.addItemToPagePosition(item,page);

                    home.desktop.consumeRevert();
                    home.dock.consumeRevert();
                    return true;
                }
                break;
        }
        return false;
    }

    //Used for saving
    public static class SimpleItem {
        public Item.Type type;
        public String actions;
        public int x = 0;
        public int y = 0;
        public String name;
        public int widgetID;
        public int spanX = 1;
        public int spanY = 1;

        public SimpleItem() {
        }

        public SimpleItem(Item in) {
            this.name = in.name;
            this.type = in.type;
            this.actions = in.getActionsAsString();
            this.x = in.x;
            this.y = in.y;
            this.spanX = in.spanX;
            this.spanY = in.spanY;
            this.widgetID = in.widgetID;
        }
    }

    public static class Item implements Parcelable {
        public static final Parcelable.Creator<Item> CREATOR = new Parcelable.Creator<Item>() {

            @Override
            public Item createFromParcel(Parcel in) {
                return new Item(in);
            }

            @Override
            public Item[] newArray(int size) {
                return new Item[size];
            }
        };

        public Type type;

        public Intent[] actions;

        public int x = 0, y = 0;

        public int widgetID;

        public String name;

        public int spanX = 1, spanY = 1;

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Item
                    && ((Item) obj).type == this.type
                    && Arrays.equals(((Item) obj).actions, this.actions)
                    && ((Item) obj).x == this.x
                    && ((Item) obj).y == this.y
                    ;
        }

        public void addActions(Intent act) {
            Intent[] newAct = new Intent[actions.length + 1];
            for (int i = 0; i < actions.length; i++) {
                newAct[i] = actions[i];
            }
            newAct[actions.length] = act;
            actions = newAct;
        }

        public void removeActions(Intent act) {
            Intent[] newAct = new Intent[actions.length - 1];
            boolean removed = false;
            for (int i = 0; i < actions.length; i++) {
                if (!act.equals(actions[i]))
                    newAct[removed ? i - 1 : i] = actions[i];
                else
                    removed = true;
            }
            actions = newAct;
        }

        public Item() {
        }

        public Item(SimpleItem in) {
            this.type = in.type;
            this.name = in.name;
            this.actions = getActionsFromString(in.actions);
            this.x = in.x;
            this.y = in.y;
            this.spanX = in.spanX;
            this.spanY = in.spanY;
            this.widgetID = in.widgetID;
        }

        public static Item newAppItem(AppManager.App app) {
            Desktop.Item item = new Item();
            item.type = Type.APP;
            item.actions = new Intent[]{toIntent(app)};
            return item;
        }

        public static Item newWidgetItem(int widgetID) {
            Desktop.Item item = new Item();
            item.type = Type.WIDGET;
            item.widgetID = widgetID;
            item.spanX = 1;
            item.spanY = 1;
            return item;
        }

        public static Item newShortcutItem(Context context, String name, Intent intent, Bitmap icon) {
            Desktop.Item item = new Item();
            item.type = Type.SHORTCUT;
            item.spanX = 1;
            item.spanY = 1;

            String iconID = Tool.saveIconAndReturnID(context, icon);
            intent.putExtra("shortCutIconID", iconID);
            intent.putExtra("shortCutName", name);

            item.actions = new Intent[]{intent};
            return item;
        }

        public static Item newShortcutItem(Intent intent) {
            Desktop.Item item = new Item();
            item.type = Type.SHORTCUT;
            item.spanX = 1;
            item.spanY = 1;

            item.actions = new Intent[]{intent};
            return item;
        }

        public static Item fromGroupItem(Item gItem) {
            Desktop.Item item = new Item();
            item.type = Type.GROUP;
            item.spanX = 1;
            item.spanY = 1;
            item.actions = gItem.actions.clone();
            return item;
        }


        public Item(Parcel in) {
            type = Type.valueOf(in.readString());
            switch (type) {
                case SHORTCUT:
                case GROUP:
                case APP:
                    actions = in.createTypedArray(Intent.CREATOR);
                    break;
                case WIDGET:
                    widgetID = in.readInt();
                    spanX = in.readInt();
                    spanY = in.readInt();
                    break;
            }
            name = in.readString();
        }

        private static Intent toIntent(AppManager.App app) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(app.packageName, app.className);
            return intent;
        }

        public String getActionsAsString() {
            if (actions == null || actions.length == 0) {
                return "";
            } else {
                StringBuilder str = new StringBuilder();
                for (int i = 0; i < actions.length; i++) {
                    str.append(actions[i].toUri(0));
                    if (i != actions.length - 1)
                        str.append("[MyActs]");
                }
                return str.toString();
            }
        }

        public static Intent[] getActionsFromString(String str) {
            if (str == null || str.isEmpty()) {
                return new Intent[0];
            } else {
                if (!str.contains("[MyActs]")) {
                    try {
                        return new Intent[]{Intent.parseUri(str, 0)};
                    } catch (URISyntaxException e) {
                        return new Intent[0];
                    }
                }
                String[] raw = Tool.split(str, "[MyActs]");
                Intent[] acts = new Intent[raw.length];
                for (int i = 0; i < acts.length; i++) {
                    try {
                        acts[i] = Intent.parseUri(raw[i], 0);
                    } catch (URISyntaxException e) {
                        return new Intent[0];
                    }
                }
                return acts;
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(type.toString());
            switch (type) {
                case SHORTCUT:
                case GROUP:
                case APP:
                    out.writeTypedArray(actions, 0);
                    break;
                case WIDGET:
                    out.writeInt(widgetID);
                    out.writeInt(spanX);
                    out.writeInt(spanY);
                    break;
            }
            out.writeString(name);
        }

        public enum Type {
            APP,
            WIDGET,
            SHORTCUT,
            GROUP
        }
    }

    public interface OnDesktopEditListener {
        void onStart();

        void onFinished();
    }

}
