/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;

import static org.gradle.util.HelperUtil.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultNamedDomainObjectContainerTest {
    private final ClassGenerator classGenerator = new AsmBackedClassGenerator();
    private final DefaultNamedDomainObjectContainer<Bean> container = classGenerator.newInstance(DefaultNamedDomainObjectContainer.class, Bean.class, classGenerator);
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void usesTypeNameToGenerateDisplayName() {
        assertThat(container.getTypeDisplayName(), equalTo("Bean"));
        assertThat(container.getDisplayName(), equalTo("Bean container"));
    }

    @Test
    public void canGetAllDomainObjectsForEmptyContainer() {
        assertTrue(container.getAll().isEmpty());
    }

    @Test
    public void canGetAllDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        assertThat(container.getAll(), equalTo(toLinkedSet(bean1, bean2, bean3)));
    }

    @Test
    public void canIterateOverEmptyContainer() {
        Iterator<Bean> iterator = container.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canIterateOverDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        Iterator<Bean> iterator = container.iterator();
        assertThat(iterator.next(), sameInstance(bean1));
        assertThat(iterator.next(), sameInstance(bean2));
        assertThat(iterator.next(), sameInstance(bean3));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canGetAllDomainObjectsAsMapForEmptyContainer() {
        assertTrue(container.getAsMap().isEmpty());
    }

    @Test
    public void canGetAllDomainObjectsAsMap() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        assertThat(container.getAsMap(), equalTo(GUtil.map("a", bean1, "b", bean2, "c", bean3)));
    }

    @Test
    public void canGetAllMatchingDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        final Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element == bean2;
            }
        };

        container.addObject("a", bean1);
        container.addObject("b", bean2);
        container.addObject("c", bean3);

        assertThat(container.findAll(spec), equalTo(toLinkedSet(bean2)));
    }

    @Test
    public void getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return false;
            }
        };

        container.addObject("a", new Bean());

        assertTrue(container.findAll(spec).isEmpty());
    }

    @Test
    public void canGetFilteredCollectionContainingAllObjectsWhichMeetSpec() {
        final Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element != bean1;
            }
        };

        TestClosure testClosure = new TestClosure() {
            public Object call(Object param) {
                return param != bean1;
            }
        };

        container.addObject("a", bean1);
        container.addObject("b", bean2);
        container.addObject("c", bean3);

        assertThat(container.matching(spec).getAll(), equalTo(toLinkedSet(bean2, bean3)));
        assertThat(container.matching(HelperUtil.toClosure(testClosure)).getAll(), equalTo(toLinkedSet(bean2, bean3)));
        assertThat(container.matching(spec).findByName("a"), nullValue());
        assertThat(container.matching(spec).findByName("b"), sameInstance(bean2));
    }

    @Test
    public void canGetFilteredCollectionContainingAllObjectsWhichHaveType() {
        class OtherBean extends Bean {
        }
        Bean bean1 = new Bean();
        OtherBean bean2 = new OtherBean();
        Bean bean3 = new Bean();

        container.addObject("c", bean3);
        container.addObject("a", bean1);
        container.addObject("b", bean2);

        assertThat(container.withType(Bean.class).getAll(), equalTo(toLinkedSet(bean1, bean2, bean3)));
        assertThat(container.withType(OtherBean.class).getAll(), equalTo(toLinkedSet(bean2)));
        assertThat(container.withType(OtherBean.class).findByName("a"), nullValue());
        assertThat(container.withType(OtherBean.class).findByName("b"), sameInstance(bean2));
    }

    @Test
    public void canExecuteActionForAllElementsInATypeFilteredCollection() {
        class OtherBean extends Bean {
        }
        final Action<OtherBean> action = context.mock(Action.class);
        Bean bean1 = new Bean();
        final OtherBean bean2 = new OtherBean();

        container.addObject("a", bean1);
        container.addObject("b", bean2);

        context.checking(new Expectations(){{
            one(action).execute(bean2);
        }});

        container.withType(OtherBean.class, action);
    }

    @Test
    public void canExecuteClosureForAllElementsInATypeFilteredCollection() {
        class OtherBean extends Bean {
        }
        final TestClosure closure = context.mock(TestClosure.class);
        Bean bean1 = new Bean();
        final OtherBean bean2 = new OtherBean();

        container.addObject("a", bean1);
        container.addObject("b", bean2);

        context.checking(new Expectations(){{
            one(closure).call(bean2);
        }});

        container.withType(OtherBean.class, HelperUtil.toClosure(closure));
    }

    @Test
    public void filteredCollectionIsLive() {
        final Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();
        Bean bean4 = new Bean();

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element != bean1;
            }
        };

        container.addObject("a", bean1);

        DomainObjectCollection<Bean> filteredCollection = container.matching(spec);
        assertTrue(filteredCollection.getAll().isEmpty());

        container.addObject("b", bean2);
        container.addObject("c", bean3);

        assertThat(filteredCollection.getAll(), equalTo(toLinkedSet(bean2, bean3)));

        container.addObject("c", bean4);

        assertThat(filteredCollection.getAll(), equalTo(toLinkedSet(bean2, bean4)));
    }

    @Test
    public void filteredCollectionExecutesActionWhenMatchingObjectAdded() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element == bean;
            }
        };

        container.matching(spec).whenObjectAdded(action);

        container.addObject("bean", bean);
        container.addObject("bean2", new Bean());
    }

    @Test
    public void filteredCollectionExecutesClosureWhenMatchingObjectAdded() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element == bean;
            }
        };

        container.matching(spec).whenObjectAdded(HelperUtil.toClosure(closure));

        container.addObject("bean", bean);
        container.addObject("bean2", new Bean());
    }

    @Test
    public void canChainFilteredCollections() {
        final Bean bean = new Bean();
        final Bean bean2 = new Bean();
        final Bean bean3 = new Bean();

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element != bean;
            }
        };
        Spec<Bean> spec2 = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element != bean2;
            }
        };

        container.addObject("a", bean);
        container.addObject("b", bean2);
        container.addObject("c", bean3);

        DomainObjectCollection<Bean> collection = container.matching(spec).matching(spec2);
        assertThat(collection.getAll(), equalTo(toSet(bean3)));
    }

    @Test
    public void canGetDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.getByName("a"), sameInstance(bean));
        assertThat(container.getAt("a"), sameInstance(bean));
    }

    @Test
    public void getDomainObjectByNameFailsForUnknownDomainObject() {
        try {
            container.getByName("unknown");
            fail();
        } catch (UnknownDomainObjectException e) {
            assertThat(e.getMessage(), equalTo("Bean with name 'unknown' not found."));
        }
    }

    @Test
    public void getDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.getByName("bean"), sameInstance(bean));
    }

    @Test
    public void canConfigureDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.getByName("a", toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void configureDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.getByName("bean", toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void canFindDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.findByName("a"), sameInstance(bean));
    }

    @Test
    public void findDomainObjectByNameReturnsNullForUnknownDomainObject() {
        assertThat(container.findByName("a"), nullValue());
    }

    @Test
    public void findDomainObjectByNameInvokesRulesForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.findByName("bean"), sameInstance(bean));
    }

    @Test
    public void findDomainObjectByNameInvokesNestedRulesOnlyOnceForUnknownDomainObject() {
        final Bean bean1 = new Bean();
        final Bean bean2 = new Bean();
        container.addRule(new Rule() {
            public String getDescription() {
                return "rule1";
            }

            public void apply(String domainObjectName) {
                if (domainObjectName.equals("bean1")) {
                    container.addObject("bean1", bean1);
                }
            }
        });
        container.addRule(new Rule() {
            private boolean applyHasBeenCalled;

            public String getDescription() {
                return "rule2";
            }

            public void apply(String domainObjectName) {
                if (domainObjectName.equals("bean2")) {
                    assertThat(applyHasBeenCalled, equalTo(false));
                    container.findByName("bean1");
                    container.findByName("bean2");
                    container.addObject("bean2", bean2);
                    applyHasBeenCalled = true;
                }
            }
        });
        container.findByName("bean2");
        assertThat(container.getAll(), equalTo(WrapUtil.toSet(bean1, bean2)));
    }

    @Test
    public void callsActionWhenObjectAdded() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.whenObjectAdded(action);
        container.addObject("bean", bean);
    }

    @Test
    public void callsClosureWhenObjectAdded() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.whenObjectAdded(HelperUtil.toClosure(closure));
        container.addObject("bean", bean);
    }

    @Test
    public void callsActionWhenObjectRemoved() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.whenObjectRemoved(action);
        container.addObject("bean", bean);
        container.addObject("bean", new Bean());
    }

    @Test
    public void allCallsActionForEachExistingObject() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.addObject("bean", bean);
        container.all(action);
    }

    @Test
    public void allCallsClosureForEachExistingObject() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.addObject("bean", bean);
        container.all(HelperUtil.toClosure(closure));
    }

    @Test
    public void allCallsActionForEachNewObject() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.all(action);
        container.addObject("bean", bean);
    }

    @Test
    public void allCallsClosureForEachNewObject() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.all(HelperUtil.toClosure(closure));
        container.addObject("bean", bean);
    }

    @Test
    public void eachObjectIsAvailableAsADynamicProperty() {
        Bean bean = new Bean();
        container.addObject("child", bean);
        assertTrue(container.getAsDynamicObject().hasProperty("child"));
        assertThat(container.getAsDynamicObject().getProperty("child"), sameInstance((Object) bean));
        assertThat(container.getAsDynamicObject().getProperties().get("child"), sameInstance((Object) bean));
        assertThat(call("{ it.child }", container), sameInstance((Object) bean));
        assertThat(call("{ it.child }", container.withType(Bean.class)), sameInstance((Object) bean));
        assertThat(call("{ it.child }", container.matching(Specs.satisfyAll())), sameInstance((Object) bean));
    }

    @Test
    public void eachObjectIsAvailableUsingAnIndex() {
        Bean bean = new Bean();
        container.addObject("child", bean);
        assertThat(call("{ it['child'] }", container), sameInstance((Object) bean));
    }

    @Test
    public void cannotGetUnknownProperty() {
        assertFalse(container.getAsDynamicObject().hasProperty("unknown"));

        try {
            container.getAsDynamicObject().getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            // expected
        }
    }

    @Test
    public void dynamicPropertyAccessInvokesRulesForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertTrue(container.getAsDynamicObject().hasProperty("bean"));
        assertThat(container.getAsDynamicObject().getProperty("bean"), sameInstance((Object) bean));
    }

    @Test
    public void eachObjectIsAvailableAsConfigureMethod() {
        Bean bean = new Bean();
        container.addObject("child", bean);

        Closure closure = toClosure("{ beanProperty = 'value' }");
        assertTrue(container.getAsDynamicObject().hasMethod("child", closure));
        container.getAsDynamicObject().invokeMethod("child", closure);
        assertThat(bean.getBeanProperty(), equalTo("value"));

        call("{ it.child { beanProperty = 'value 2' } }", container);
        assertThat(bean.getBeanProperty(), equalTo("value 2"));

        call("{ it.invokeMethod('child') { beanProperty = 'value 3' } }", container);
        assertThat(bean.getBeanProperty(), equalTo("value 3"));
    }

    @Test
    public void canUseDynamicPropertiesAndMethodsInsideConfigureClosures() {
        Bean bean = new Bean();
        container.addObject("child", bean);
        container.addObject("aProp", bean);
        container.addObject("a", bean);
        container.addObject("withType", bean);
        container.addObject("allObjects", bean);

        ConfigureUtil.configure(toClosure("{ child.beanProperty = 'value 1' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 1"));

        ConfigureUtil.configure(toClosure("{ child { beanProperty = 'value 2' } }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 2"));

        ConfigureUtil.configure(toClosure("{ aProp.beanProperty = 'value 3' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 3"));

        ConfigureUtil.configure(toClosure("{ a.beanProperty = 'value 4' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 4"));

        // Try with an element with the same name as a method
        ConfigureUtil.configure(toClosure("{ withType.beanProperty = 'value 6' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 6"));

        ConfigureUtil.configure(toClosure("{ withType { beanProperty = 'value 6' } }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 6"));
    }

    @Test
    public void cannotInvokeUnknownMethod() {
        container.addObject("child", new Bean());

        assertMethodUnknown("unknown");
        assertMethodUnknown("unknown", toClosure("{ }"));
        assertMethodUnknown("child");
        assertMethodUnknown("child", "not a closure");
        assertMethodUnknown("child", toClosure("{ }"), "something else");
    }

    private void assertMethodUnknown(String name, Object... arguments) {
        assertFalse(container.getAsDynamicObject().hasMethod(name, arguments));
        try {
            container.getAsDynamicObject().invokeMethod(name, arguments);
            fail();
        } catch (groovy.lang.MissingMethodException e) {
            // Expected
        }
    }

    @Test
    public void configureMethodInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertTrue(container.getAsDynamicObject().hasMethod("bean", toClosure("{ }")));
    }

    @Test
    public void addRuleByClosure() {
        String testPropertyKey = "org.gradle.test.addRuleByClosure";
        String expectedTaskName = "someTaskName";
        Closure ruleClosure = HelperUtil.toClosure(String.format("{ taskName -> System.setProperty('%s', '%s') }",
                testPropertyKey, expectedTaskName));
        container.addRule("description", ruleClosure);
        container.getRules().get(0).apply(expectedTaskName);
        assertThat(System.getProperty(testPropertyKey), equalTo(expectedTaskName));
        System.getProperties().remove(testPropertyKey);
    }

    private void addRuleFor(final Bean bean) {
        container.addRule(new Rule() {
            public String getDescription() {
                throw new UnsupportedOperationException();
            }

            public void apply(String taskName) {
                container.addObject(taskName, bean);
            }
        });
    }

    private class Bean {
        private String beanProperty;

        public String getBeanProperty() {
            return beanProperty;
        }

        public void setBeanProperty(String beanProperty) {
            this.beanProperty = beanProperty;
        }
    }
}
