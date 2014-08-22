/**
 * This file is part of mycollab-mobile.
 *
 * mycollab-mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-mobile.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.mobile;

import static com.esofthead.mycollab.common.MyCollabSession.CURRENT_APP;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esofthead.mycollab.common.i18n.GenericI18Enum;
import com.esofthead.mycollab.configuration.PasswordEncryptHelper;
import com.esofthead.mycollab.core.UserInvalidInputException;
import com.esofthead.mycollab.eventmanager.EventBusFactory;
import com.esofthead.mycollab.mobile.module.user.events.UserEvent;
import com.esofthead.mycollab.mobile.shell.ShellController;
import com.esofthead.mycollab.mobile.shell.events.ShellEvent;
import com.esofthead.mycollab.mobile.ui.ConfirmDialog;
import com.esofthead.mycollab.module.billing.UsageExceedBillingPlanException;
import com.esofthead.mycollab.vaadin.AppContext;
import com.esofthead.mycollab.vaadin.MyCollabUI;
import com.esofthead.mycollab.vaadin.mvp.ControllerRegistry;
import com.esofthead.mycollab.vaadin.ui.NotificationUtil;
import com.esofthead.vaadin.mobilecomponent.MobileNavigationManager;
import com.vaadin.addon.touchkit.extensions.LocalStorage;
import com.vaadin.addon.touchkit.extensions.LocalStorageCallback;
import com.vaadin.addon.touchkit.ui.NavigationManager;
import com.vaadin.addon.touchkit.ui.NavigationManager.NavigationEvent;
import com.vaadin.addon.touchkit.ui.NavigationManager.NavigationEvent.Direction;
import com.vaadin.addon.touchkit.ui.NavigationView;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.server.DefaultErrorHandler;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.Component;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

/**
 * 
 * @author MyCollab Ltd.
 * @since 3.0
 * 
 */
@Theme("mycollab-mobile")
@Widgetset("com.esofthead.mycollab.widgetset.MyCollabMobileWidgetSet")
public class MobileApplication extends MyCollabUI {
	private static final long serialVersionUID = 1L;

	public static final String LOGIN_DATA = "m_login";

	private static Logger log = LoggerFactory
			.getLogger(MobileApplication.class);

	@Override
	protected void init(VaadinRequest request) {
		log.debug("Init mycollab mobile application {}", this.toString());

		VaadinSession.getCurrent().setErrorHandler(new DefaultErrorHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public void error(com.vaadin.server.ErrorEvent event) {
				Throwable e = event.getThrowable();
				UserInvalidInputException invalidException = (UserInvalidInputException) getExceptionType(
						e, UserInvalidInputException.class);
				if (invalidException != null) {
					NotificationUtil.showWarningNotification(AppContext
							.getMessage(
									GenericI18Enum.ERROR_USER_INPUT_MESSAGE,
									invalidException.getMessage()));
				} else {

					UsageExceedBillingPlanException usageBillingException = (UsageExceedBillingPlanException) getExceptionType(
							e, UsageExceedBillingPlanException.class);
					if (usageBillingException != null) {
						if (AppContext.isAdmin()) {
							ConfirmDialog.show(
									UI.getCurrent(),
									AppContext
											.getMessage(GenericI18Enum.EXCEED_BILLING_PLAN_MSG_FOR_ADMIN),
									AppContext
											.getMessage(GenericI18Enum.BUTTON_YES_LABEL),
									AppContext
											.getMessage(GenericI18Enum.BUTTON_NO_LABEL),
									new ConfirmDialog.CloseListener() {
										private static final long serialVersionUID = 1L;

										@Override
										public void onClose(ConfirmDialog dialog) {
											if (dialog.isConfirmed()) {
												Collection<Window> windowsList = UI
														.getCurrent()
														.getWindows();
												for (Window window : windowsList) {
													window.close();
												}
												EventBusFactory
														.getInstance()
														.post(new ShellEvent.GotoUserAccountModule(
																this,
																new String[] { "billing" }));
											}
										}
									});

						} else {
							NotificationUtil.showErrorNotification(AppContext
									.getMessage(GenericI18Enum.EXCEED_BILLING_PLAN_MSG_FOR_USER));
						}
					} else {
						log.error("Error", e);
						NotificationUtil.showErrorNotification(AppContext
								.getMessage(GenericI18Enum.ERROR_USER_NOTICE_INFORMATION_MESSAGE));
					}
				}

			}
		});

		initialUrl = this.getPage().getUriFragment();
		VaadinSession.getCurrent().setAttribute(CURRENT_APP, this);
		currentContext = new AppContext(this);
		postSetupApp(request);
		try {
			currentContext.initDomain(initialSubDomain);
		} catch (Exception e) {
			// TODO: show content notice user there is no existing domain
			return;
		}

		final MobileNavigationManager manager = new MobileNavigationManager();
		manager.addNavigationListener(new NavigationManager.NavigationListener() {
			private static final long serialVersionUID = -2317588983851761998L;

			@Override
			public void navigate(NavigationEvent event) {
				NavigationManager currentNavigator = (NavigationManager) event
						.getSource();
				if (event.getDirection() == Direction.BACK) {
					Component nextComponent = currentNavigator
							.getNextComponent();
					if (nextComponent instanceof NavigationView) {
						((NavigationView) nextComponent)
								.setPreviousComponent(null);
					}
					currentNavigator.removeComponent(nextComponent);
					currentNavigator.getState().setNextComponent(null);
				}
			}
		});
		setContent(manager);

		registerControllers(manager);
		checkLocalData();
	}

	private void checkLocalData() {
		LocalStorage.detectValue(LOGIN_DATA, new LocalStorageCallback() {
			private static final long serialVersionUID = 3217947479690600476L;

			@Override
			public void onSuccess(String value) {
				if (value != null) {
					String[] loginParams = value.split("\\$");
					EventBusFactory.getInstance().post(
							new UserEvent.PlainLogin(this, new String[] {
									loginParams[0],
									PasswordEncryptHelper
											.decryptText(loginParams[1]),
									String.valueOf(false) }));

				} else {
					EventBusFactory.getInstance().post(
							new ShellEvent.GotoLoginView(this, null));
				}
			}

			@Override
			public void onFailure(FailureEvent error) {
				EventBusFactory.getInstance().post(
						new ShellEvent.GotoLoginView(this, null));
			}
		});
	}

	private void registerControllers(NavigationManager manager) {
		ControllerRegistry.addController(new ShellController(manager));
	}

	private static Throwable getExceptionType(Throwable e,
			Class<? extends Throwable> exceptionType) {
		if (exceptionType.isAssignableFrom(e.getClass())) {
			return e;
		} else if (e.getCause() != null) {
			return getExceptionType(e.getCause(), exceptionType);
		} else {
			return null;
		}
	}
}
