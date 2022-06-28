/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.litho.accessibility.ImportantForAccessibility
import com.facebook.litho.accessibility.accessibilityRole
import com.facebook.litho.accessibility.accessibilityRoleDescription
import com.facebook.litho.accessibility.contentDescription
import com.facebook.litho.accessibility.importantForAccessibility
import com.facebook.litho.accessibility.onInitializeAccessibilityNodeInfo
import com.facebook.litho.animated.alpha
import com.facebook.litho.core.height
import com.facebook.litho.core.heightPercent
import com.facebook.litho.core.width
import com.facebook.litho.core.widthPercent
import com.facebook.litho.flexbox.flex
import com.facebook.litho.testing.LithoViewRule
import com.facebook.litho.testing.match
import com.facebook.litho.view.focusable
import com.facebook.litho.view.onClick
import com.facebook.litho.view.viewTag
import com.facebook.litho.visibility.onVisible
import com.facebook.rendercore.RenderUnit
import com.facebook.rendercore.testing.ViewAssertions
import com.facebook.yoga.YogaEdge
import com.nhaarman.mockitokotlin2.mock
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4::class)
class MountableComponentsTest {

  @Rule @JvmField val lithoViewRule = LithoViewRule()
  @Rule @JvmField val expectedException = ExpectedException.none()

  @Test
  fun `should render mountable component`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val root =
        Column.create(c)
            .child(
                Wrapper.create(c)
                    .widthPx(100)
                    .heightPx(100)
                    .delegate(TestViewMountableComponent(TextView(c.androidContext), steps))
                    .paddingPx(YogaEdge.ALL, 20)
                    .backgroundColor(Color.LTGRAY)
                    .border(
                        Border.create(c)
                            .widthPx(YogaEdge.ALL, 5)
                            .color(YogaEdge.ALL, Color.BLACK)
                            .build()))
            .build()

    val testView = lithoViewRule.render { root }
    testView.lithoView.unmountAllItems()

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT,
            LifecycleStep.ON_UNMOUNT)
  }

  @Test
  fun `width, height, focusable, viewTag styles respected when set`() {
    val testComponent =
        TestViewMountableComponent(
            TextView(lithoViewRule.context.androidContext),
            style = Style.width(667.px).height(668.px).focusable(true).viewTag("test_view_tag"))

    val testView = lithoViewRule.render { testComponent }

    assertThat(testView.lithoView.childCount).isEqualTo(1)
    val realTestView = testView.lithoView.getChildAt(0)

    ViewAssertions.assertThat(realTestView).matches(match<TextView> { bounds(0, 0, 667, 668) })

    assertThat(realTestView.isFocusable).isTrue()

    testView.findViewWithTag("test_view_tag")
  }

  @Test
  fun `onClick event is dispatched when set`() {
    val wasClicked = AtomicBoolean(false)

    val testComponent =
        TestViewMountableComponent(
            TextView(lithoViewRule.context.androidContext),
            style =
                Style.width(667.px).height(668.px).focusable(true).viewTag("click_me").onClick {
                  wasClicked.set(true)
                })

    val testView = lithoViewRule.render { testComponent }

    assertThat(wasClicked.get()).isFalse()
    testView.findViewWithTag("click_me").performClick()
    assertThat(wasClicked.get()).isTrue()
  }

  @Test
  fun `onVisible event is fired when set`() {
    val eventFired = AtomicBoolean(false)

    val testComponent =
        TestViewMountableComponent(
            TextView(lithoViewRule.context.androidContext),
            style =
                Style.width(667.px).height(668.px).focusable(true).viewTag("click_me").onVisible {
                  eventFired.set(true)
                })

    lithoViewRule.render { testComponent }

    assertThat(eventFired.get()).isTrue()
  }

  @Test
  fun `widthPercent and heightPercent is respected when set`() {
    val testComponent =
        TestViewMountableComponent(
            TextView(lithoViewRule.context.androidContext),
            style = Style.heightPercent(50f).widthPercent(50f))

    val testView =
        lithoViewRule.render {
          Row(style = Style.width(100.px).height(100.px)) { child(testComponent) }
        }

    assertThat(testView.lithoView.childCount).isEqualTo(1)
    val realTestView = testView.lithoView.getChildAt(0)

    ViewAssertions.assertThat(realTestView).matches(match<TextView> { bounds(0, 0, 50, 50) })
  }

  @Test
  fun `dynamic alpha is respected when set`() {
    val alpha = 0.5f
    val alphaDV: DynamicValue<Float> = DynamicValue<Float>(alpha)

    val testComponent =
        TestViewMountableComponent(
            TextView(lithoViewRule.context.androidContext),
            style = Style.width(100.px).height(100.px).alpha(alphaDV))

    val testView = lithoViewRule.render { testComponent }

    assertThat(testView.lithoView.alpha).isEqualTo(alpha)

    alphaDV.set(1f)
    assertThat(testView.lithoView.alpha).isEqualTo(1f)

    alphaDV.set(0.7f)
    assertThat(testView.lithoView.alpha).isEqualTo(0.7f)
  }

  @Test
  fun `updating the state in mountable takes effect`() {
    lateinit var stateRef: AtomicReference<String>

    class TestComponent(val view: View) : MountableComponent() {
      override fun ComponentScope.render(): MountableWithStyle {
        val testState: State<String> = useState { "initial" }
        stateRef = AtomicReference(testState.value)
        return MountableWithStyle(
            ViewMountable(view = view, updateState = { testState.update { s -> s + "_" + it } }),
            style = null)
      }
    }

    lithoViewRule.render { TestComponent(TextView(lithoViewRule.context.androidContext)) }

    lithoViewRule.idle()

    assertThat(stateRef.get())
        .describedAs("String state is updated")
        .isEqualTo("initial_createContent_mount")
  }

  @Test
  fun `should not remeasure same mountable if size specs match`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val component =
        TestViewMountableComponent(
            view = view,
            steps = steps,
            style = Style.width(100.px).height(100.px),
        )

    val testView = lithoViewRule.render { Column.create(c).child(component).build() }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT)

    steps.clear()

    lithoViewRule.render(lithoView = testView.lithoView) {
      Column.create(c).child(component).build()
    }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.SHOULD_UPDATE,
            LifecycleStep.ON_UNMOUNT,
            LifecycleStep.ON_MOUNT)
  }

  @Test
  fun `should not remeasure same mountable if size specs match with non exact size`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val component =
        TestViewMountableComponent(
            view = view,
            steps = steps,
            style = Style.width(100.px).flex(grow = 1f),
        )

    val testView = lithoViewRule.render { Column.create(c).child(component).build() }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT)

    steps.clear()

    lithoViewRule.render(lithoView = testView.lithoView) {
      Column.create(c).child(component).build()
    }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.SHOULD_UPDATE,
            LifecycleStep.ON_UNMOUNT,
            LifecycleStep.ON_MOUNT)
  }

  @Test
  fun `should remeasure mountable if properties have changed`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)

    val testView =
        lithoViewRule.render {
          Column.create(c)
              .child(
                  TestViewMountableComponent(
                      identity = 0,
                      view = view,
                      steps = steps,
                      style = Style.width(100.px).flex(grow = 1f),
                  ))
              .build()
        }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT)

    steps.clear()

    lithoViewRule.render(lithoView = testView.lithoView) {
      Column.create(c)
          .child(
              TestViewMountableComponent(
                  identity = 1,
                  view = view,
                  steps = steps,
                  style = Style.width(100.px).flex(grow = 1f),
              ))
          .build()
    }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.SHOULD_UPDATE,
            LifecycleStep.ON_UNMOUNT,
            LifecycleStep.ON_MOUNT)
  }

  @Test
  fun `should not remeasure comparable mountable if the equivalence passes`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)

    val testView =
        lithoViewRule.render {
          Column.create(c)
              .child(
                  TestViewMountableComponent(
                      identity = 0,
                      view = view,
                      steps = steps,
                      style = Style.width(100.px).flex(grow = 1f),
                      shouldUseComparableMountable = true,
                  ))
              .build()
        }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT)

    steps.clear()

    lithoViewRule.render(lithoView = testView.lithoView) {
      Column.create(c)
          .child(
              TestViewMountableComponent(
                  identity = 0, // ensures that equivalence call is true
                  view = TextView(c.androidContext), // ensure that field field equals fails
                  steps = steps,
                  style = Style.width(100.px).flex(grow = 1f),
                  shouldUseComparableMountable = true,
              ))
          .build()
    }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.SHOULD_UPDATE,
            LifecycleStep.ON_UNMOUNT,
            LifecycleStep.ON_MOUNT)
  }

  @Test
  fun `should remeasure mountable if size specs change`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val component = TestViewMountableComponent(identity = 0, view = view, steps = steps)

    val testView = lithoViewRule.render(widthPx = 800, heightPx = 600) { component }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.ON_CREATE_MOUNT_CONTENT,
            LifecycleStep.ON_MOUNT)

    steps.clear()

    lithoViewRule.render(lithoView = testView.lithoView, widthPx = 1920, heightPx = 1080) {
      component
    }

    assertThat(LifecycleStep.getSteps(steps))
        .containsExactly(
            LifecycleStep.RENDER,
            LifecycleStep.ON_MEASURE,
            LifecycleStep.SHOULD_UPDATE,
            LifecycleStep.ON_UNMOUNT,
            LifecycleStep.ON_MOUNT)
  }

  @Test
  fun `controller should set and get props on the content`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val controller = ViewController()
    val root =
        TestViewMountableComponent(
            identity = 0,
            view = TextView(c.androidContext),
            steps = steps,
            controller = controller,
            shouldUseComparableMountable = true,
            style = Style.width(100.px).height(100.px))

    val testView = lithoViewRule.render { root }

    controller.setTag("tag")

    val view = testView.findViewWithTag("tag")

    assertThat(view.tag).isEqualTo("tag")
    assertThat(controller.getTag()).isEqualTo("tag")
  }

  @Test
  fun `controller should unbind after unmount`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val controller = ViewController()
    val root =
        TestViewMountableComponent(
            identity = 0,
            view = TextView(c.androidContext),
            steps = steps,
            controller = controller,
            shouldUseComparableMountable = true,
            style = Style.width(100.px).height(100.px))

    val testView = lithoViewRule.render { root }

    controller.setTag("tag")

    testView.lithoView.setComponentTree(null, true)

    assertThat(controller.getTag()).isNull()
  }

  @Test
  fun `new controller should replace old controller`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val controller1 = ViewController()
    val root1 =
        TestViewMountableComponent(
            identity = 0,
            view = TextView(c.androidContext),
            steps = steps,
            controller = controller1,
            shouldUseComparableMountable = true,
            style = Style.width(100.px).height(100.px))

    val testView = lithoViewRule.render { root1 }

    controller1.setTag("tag1")
    assertThat(controller1.getTag()).isEqualTo("tag1")

    val controller2 = ViewController()
    val root2 =
        TestViewMountableComponent(
            identity = 0,
            view = TextView(c.androidContext),
            steps = steps,
            controller = controller2,
            shouldUseComparableMountable = true,
            style = Style.width(100.px).height(100.px))

    lithoViewRule.render(lithoView = testView.lithoView) { root2 }

    controller1.setTag("random")
    assertThat(controller1.getTag()).isNull()

    controller2.setTag("tag2")
    assertThat(controller2.getTag()).isEqualTo("tag2")
  }

  @Test
  fun `registerController should throw if controller already registered`() {
    expectedException.expect(RuntimeException::class.java)
    expectedException.expectMessage("A controller is already registered for this Mountable")

    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val controller1 = ViewController()
    val controller2 = ViewController()
    val root =
        TestViewMountableComponent(
            identity = 0,
            view = TextView(c.androidContext),
            steps = steps,
            controller = controller1,
            controller2 = controller2,
            shouldUseComparableMountable = true,
            style = Style.width(100.px).height(100.px))

    lithoViewRule.render { root }

    controller1.setTag("tag1")
    assertThat(controller1.getTag()).isNull()

    controller2.setTag("tag2")
    assertThat(controller2.getTag()).isEqualTo("tag2")
  }

  @Test
  fun `same instance should be equivalent`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val component = TestViewMountableComponent(identity = 0, view = view, steps = steps)

    assertThat(component.isEquivalentTo(component)).isTrue
    assertThat(component.isEquivalentTo(component, true)).isTrue
  }

  @Test
  fun `components with same prop values should be equivalent`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val a = TestViewMountableComponent(identity = 0, view = view, steps = steps)
    val b = TestViewMountableComponent(identity = 0, view = view, steps = steps)
    assertThat(a.isEquivalentTo(b)).isTrue
    assertThat(a.isEquivalentTo(b, true)).isTrue
  }

  @Test
  fun `components with different prop values should not be equivalent`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val a = TestViewMountableComponent(identity = 0, view = view, steps = steps)
    val b = TestViewMountableComponent(identity = 1, view = view, steps = steps)

    assertThat(a.isEquivalentTo(b)).isFalse
    assertThat(a.isEquivalentTo(b, true)).isFalse
  }

  @Test
  fun `components with different style values should not be equivalent`() {
    val c = lithoViewRule.context
    val steps = mutableListOf<LifecycleStep.StepInfo>()
    val view = TextView(c.androidContext)
    val a =
        TestViewMountableComponent(
            identity = 0,
            view = view,
            steps = steps,
            style = Style.width(100.px).height(100.px), /* 100 here */
        )

    val b =
        TestViewMountableComponent(
            identity = 0,
            view = view,
            steps = steps,
            style = Style.width(200.px).height(200.px), /* 200 here */
        )

    assertThat(a.isEquivalentTo(b, true)).isFalse
  }

  @Test
  fun `when a11y props are set on style it should set them on the rendered content`() {
    val eventHandler: EventHandler<OnInitializeAccessibilityNodeInfoEvent> = mock()

    val component =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            style =
                Style.accessibilityRole(AccessibilityRole.EDIT_TEXT)
                    .accessibilityRoleDescription("Accessibility Test")
                    .contentDescription("Accessibility Test")
                    .importantForAccessibility(ImportantForAccessibility.YES)
                    .onInitializeAccessibilityNodeInfo { eventHandler })

    // verify that info is set on the LithoView where possible, otherwise on LithoNode
    val testView = lithoViewRule.render { component }
    val node = testView.currentRootNode?.node
    val nodeInfo = node?.nodeInfo
    assertThat(nodeInfo?.accessibilityRole).isEqualTo(AccessibilityRole.EDIT_TEXT)
    assertThat(nodeInfo?.accessibilityRoleDescription).isEqualTo("Accessibility Test")
    assertThat(testView.lithoView.getChildAt(0).contentDescription).isEqualTo("Accessibility Test")
    assertThat(testView.lithoView.getChildAt(0).importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    assertThat(nodeInfo?.onInitializeAccessibilityNodeInfoHandler).isNotNull
  }

  @Test
  fun `TestDrawableMountableComponent has IMAGE accessibility role by default but overriding it works`() {
    val component1 =
        TestDrawableMountableComponent(
            drawable = ColorDrawable(Color.RED), style = Style.width(100.px).height(100.px))

    val testView1 = lithoViewRule.render { component1 }
    val node1 = testView1.currentRootNode?.node
    val nodeInfo1 = node1?.nodeInfo
    assertThat(nodeInfo1?.accessibilityRole).isEqualTo(AccessibilityRole.IMAGE)

    val component2 =
        TestDrawableMountableComponent(
            drawable = ColorDrawable(Color.RED),
            style =
                Style.width(100.px)
                    .height(100.px)
                    .accessibilityRole(AccessibilityRole.IMAGE_BUTTON))

    val testView2 = lithoViewRule.render { component2 }
    val node2 = testView2.currentRootNode?.node
    val nodeInfo2 = node2?.nodeInfo
    assertThat(nodeInfo2?.accessibilityRole).isEqualTo(AccessibilityRole.IMAGE_BUTTON)
  }

  @Test
  fun `when dynamic value is set if should update the content`() {
    val tag = DynamicValue<Any?>("0")
    val root =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            dynamicTag = tag,
            style = Style.width(100.px).height(100.px))

    val test = lithoViewRule.render { root }

    test.findViewWithTag("0")

    tag.set("1")

    test.findViewWithTag("1")
  }

  @Test
  fun `when component with dynamic value is unmounted it should unbind the dynamic value`() {
    val tag = DynamicValue<Any?>("0")
    val root =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            dynamicTag = tag,
            style = Style.width(100.px).height(100.px))

    val test = lithoViewRule.render { root }

    val view = test.findViewWithTag("0")

    test.lithoView.setComponentTree(null, true)

    assertThat(tag.numberOfListeners).isEqualTo(0)

    tag.set("1")

    // tag should be set to default value
    assertThat(view.tag).isEqualTo("default_value")
  }

  @Test
  fun `when new dynamic value is set it should unbind the old dynamic value`() {
    val tag1 = DynamicValue<Any?>("0")
    val root1 =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            dynamicTag = tag1,
            style = Style.width(100.px).height(100.px))

    val test = lithoViewRule.render { root1 }

    test.findViewWithTag("0")

    tag1.set("1")

    test.findViewWithTag("1")

    assertThat(tag1.numberOfListeners).isEqualTo(1)

    val tag2 = DynamicValue<Any?>("2")
    val root2 =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            dynamicTag = tag2,
            style = Style.width(100.px).height(100.px))

    test.setRoot(root2)

    assertThat(tag1.numberOfListeners).isEqualTo(0)

    // should have view with new tag
    val view = test.findViewWithTag("2")

    // set new tag using the old dynamic value
    tag1.set("3")

    // the above should not work, the tag should not change
    assertThat(view.tag).isEqualTo("2")

    // set the new tag using the new dynamic value
    tag2.set("3")

    // the above should work, the tag should change
    assertThat(view.tag).isEqualTo("3")

    assertThat(tag2.numberOfListeners).isEqualTo(1)
  }

  @Test
  fun `when same dynamic value is used on different components it should update the content for all instances`() {
    val c = lithoViewRule.context
    val tag = DynamicValue<Any?>("0")
    val root =
        Column.create(c)
            .child(
                TestViewMountableComponent(
                    EditText(lithoViewRule.context.androidContext),
                    dynamicTag = tag,
                    style = Style.width(100.px).height(100.px)))
            .child(
                TestViewMountableComponent(
                    EditText(lithoViewRule.context.androidContext),
                    dynamicTag = tag,
                    style = Style.width(100.px).height(100.px)))
            .build()

    val test = lithoViewRule.render { root }

    val lithoView = test.lithoView
    val child0 = lithoView.getChildAt(0)
    val child1 = lithoView.getChildAt(1)

    assertThat(child0.tag).isEqualTo("0")
    assertThat(child1.tag).isEqualTo("0")

    tag.set("1")

    assertThat(child0.tag).isEqualTo("1")
    assertThat(child1.tag).isEqualTo("1")
  }

  @Test
  fun `when same component with dynamic value is used multiple times it should update the content for all instances`() {
    val c = lithoViewRule.context
    val tag = DynamicValue<Any?>("0")
    val component =
        TestViewMountableComponent(
            EditText(lithoViewRule.context.androidContext),
            dynamicTag = tag,
            style = Style.width(100.px).height(100.px))
    val root = Column.create(c).child(component).child(component).build()

    val test = lithoViewRule.render { root }

    val lithoView = test.lithoView
    val child0 = lithoView.getChildAt(0)
    val child1 = lithoView.getChildAt(1)

    assertThat(child0.tag).isEqualTo("0")
    assertThat(child1.tag).isEqualTo("0")

    tag.set("1")

    assertThat(child0.tag).isEqualTo("1")
    assertThat(child1.tag).isEqualTo("1")
  }
}

class TestViewMountableComponent(
    val view: View,
    val steps: MutableList<LifecycleStep.StepInfo>? = null,
    val identity: Int = 0,
    val controller: ViewController? = null,
    val controller2: ViewController? = null,
    val shouldUseComparableMountable: Boolean = false,
    val dynamicTag: DynamicValue<Any?>? = null,
    val style: Style? = null
) : MountableComponent() {

  override fun ComponentScope.render(): MountableWithStyle {

    steps?.add(LifecycleStep.StepInfo(LifecycleStep.RENDER))

    return MountableWithStyle(
        if (shouldUseComparableMountable) {
          ComparableViewMountable(
              identity, view, steps, controller = controller, controller2 = controller2)
        } else {
          ViewMountable(identity, view, steps, controller = controller, dynamicTag = dynamicTag)
        },
        style)
  }
}

open class ViewMountable(
    open val id: Int = 0,
    open val view: View,
    open val steps: MutableList<LifecycleStep.StepInfo>? = null,
    open val updateState: ((String) -> Unit)? = null,
    open val controller: ViewController? = null,
    open val controller2: ViewController? = null,
    val dynamicTag: DynamicValue<Any?>? = null,
    val defaultTagValue: Any? = "default_value",
) : SimpleMountable<View>() {

  init {
    controller?.let { registerController(controller as Controller<View>) }
    dynamicTag?.let {
      subscribeToMountDynamicValue(dynamicTag, defaultTagValue) { content, value ->
        content.tag = value
      }
    }
  }

  override fun createContent(context: Context): View {
    updateState?.invoke("createContent")
    steps?.add(LifecycleStep.StepInfo(LifecycleStep.ON_CREATE_MOUNT_CONTENT))
    return view
  }

  override fun measure(
      context: ComponentContext,
      widthSpec: Int,
      heightSpec: Int,
      previousLayoutData: Any?,
  ): MeasureResult {
    steps?.add(LifecycleStep.StepInfo(LifecycleStep.ON_MEASURE))
    val width =
        if (SizeSpec.getMode(widthSpec) == SizeSpec.EXACTLY) {
          SizeSpec.getSize(widthSpec)
        } else {
          100
        }

    val height =
        if (SizeSpec.getMode(heightSpec) == SizeSpec.EXACTLY) {
          SizeSpec.getSize(heightSpec)
        } else {
          100
        }

    return MeasureResult(width, height, TestLayoutData(width, height))
  }

  override fun mount(c: Context, content: View, layoutData: Any?) {
    updateState?.invoke("mount")
    steps?.add(LifecycleStep.StepInfo(LifecycleStep.ON_MOUNT))
    layoutData as TestLayoutData
  }

  override fun unmount(c: Context, content: View, layoutData: Any?) {
    steps?.add(LifecycleStep.StepInfo(LifecycleStep.ON_UNMOUNT))
    layoutData as TestLayoutData
  }

  override fun shouldUpdate(
      currentMountable: SimpleMountable<View>,
      newMountable: SimpleMountable<View>,
      currentLayoutData: Any?,
      nextLayoutData: Any?
  ): Boolean {
    steps?.add(LifecycleStep.StepInfo(LifecycleStep.SHOULD_UPDATE))
    currentMountable as ViewMountable
    newMountable as ViewMountable
    return true
  }

  override fun getRenderType(): RenderUnit.RenderType = RenderUnit.RenderType.VIEW
}

class ComparableViewMountable(
    override val id: Int = 0,
    override val view: View,
    override val steps: MutableList<LifecycleStep.StepInfo>? = null,
    override val updateState: ((String) -> Unit)? = null,
    override val controller: ViewController? = null,
    override val controller2: ViewController? = null,
) : ViewMountable(id, view, steps, updateState) {

  init {
    controller?.let { registerController(controller as Controller<View>) }
    controller2?.let { registerController(controller2 as Controller<View>) }
  }

  override fun isEquivalentTo(other: Mountable<*>): Boolean {
    return id == (other as ViewMountable).id
  }
}

class TestDrawableMountableComponent(val drawable: Drawable, val style: Style? = null) :
    MountableComponent() {

  override fun ComponentScope.render(): MountableWithStyle {
    return MountableWithStyle(
        DrawableMountable(drawable), Style.accessibilityRole(AccessibilityRole.IMAGE) + style)
  }
}

class DrawableMountable(
    val drawable: Drawable,
) : SimpleMountable<Drawable>() {

  override fun createContent(context: Context): Drawable {
    return drawable
  }

  override fun measure(
      context: ComponentContext,
      widthSpec: Int,
      heightSpec: Int,
      previousLayoutData: Any?,
  ): MeasureResult {
    val width =
        if (SizeSpec.getMode(widthSpec) == SizeSpec.EXACTLY) {
          SizeSpec.getSize(widthSpec)
        } else {
          100
        }

    val height =
        if (SizeSpec.getMode(heightSpec) == SizeSpec.EXACTLY) {
          SizeSpec.getSize(heightSpec)
        } else {
          100
        }

    return MeasureResult(width, height, TestLayoutData(width, height))
  }

  override fun mount(c: Context, content: Drawable, layoutData: Any?) {
    layoutData as TestLayoutData
  }

  override fun unmount(c: Context, content: Drawable, layoutData: Any?) {
    layoutData as TestLayoutData
  }

  override fun shouldUpdate(
      currentMountable: SimpleMountable<Drawable>,
      newMountable: SimpleMountable<Drawable>,
      currentLayoutData: Any?,
      nextLayoutData: Any?
  ): Boolean {
    currentMountable as DrawableMountable
    newMountable as DrawableMountable
    return true
  }

  override fun getRenderType(): RenderUnit.RenderType = RenderUnit.RenderType.DRAWABLE
}

class TestLayoutData(val width: Int, val height: Int)

class ViewController : Controller<View>() {

  fun getTag(): Any? {
    return content?.tag
  }

  fun setTag(tag: Any?) {
    content?.tag = tag
  }
}
