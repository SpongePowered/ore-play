//=====> CONSTANTS

const KEY_ENTER = 13;

//=====> EXTERNAL CONSTANTS

let csrf = null;
let isLoggedIn = false;


//=====> SETUP

const clipboardManager = new ClipboardJS('.copy-url');
clipboardManager.on('success', function(e) {
    const element = $('.btn-download').tooltip({title: 'Copied!', placement: 'bottom', trigger: 'manual'}).tooltip('show');
    setTimeout(function () {
        element.tooltip('destroy');
    }, 2200);
});


//=====> HELPER FUNCTIONS

function sanitize(html) {
    return $('<textarea>').html(html).text();
}

function decodeHtml(html) {
    // lol
    return $('<textarea>').html(html).val();
}

function go(str) {
    window.location = decodeHtml(str);
}

function clearUnread(e) {
    e.find('.unread').remove();
    if (!$('.user-dropdown .unread').length) $('.unread').remove();
}

function initTooltips() {
    $('[data-toggle="tooltip"]').tooltip({
        container: "body",
        delay: { "show": 500 }
    });
}

function slugify(name) {
    return name.trim().replace(/ +/g, ' ').replace(/ /g, '-');
}

function toggleSpinner(e) {
    return e.toggleClass('fa-spinner').toggleClass('fa-spin');
}

function apiV2Request(url, method, data, isRetry) {
    return getApiSession().then(function (session) {
        return new Promise(function (resolve, reject) {
            if(!data) {
                data = {};
            }

            const isFormData = data instanceof FormData;
            let allData;
            if(isFormData) {
                data.append('csrfToken', csrf);
                allData = data;
            }
            else {
                allData = Object.assign(data, {csrfToken: csrf});
            }

            $.ajax({
                url: '/api/v2/' + url,
                method: method,
                dataType: 'json',
                contentType: isFormData ? false : 'application/json',
                data: isFormData ? allData : JSON.stringify(allData),
                processData: isFormData ? false : undefined,
                headers: {'Authorization': 'ApiSession ' + session}
            }).done(function (data) {
                resolve(data);
            }).fail(function (xhr) {
                if (xhr.responseJSON && (xhr.responseJSON.error === 'Api session expired' || xhr.responseJSON.error === 'Invalid session')) {
                    if (isRetry === true) {
                        reject('Api session expired twice')
                    } else {
                        invalidateApiSession();
                        apiV2Request(url, method, data, true).then(function (data) {
                            resolve(data);
                        }).catch(function (error) {
                            reject(error);
                        });
                    }
                } else {
                    reject(xhr.statusText)
                }
            })
        })
    });
}

function getApiSession() {
    return new Promise(function (resolve, reject) {
        let session;
        if (isLoggedIn) {
            session = localStorage.getItem('api_session');
            if (session === null) {
                return $.ajax({
                    url: '/api/v2/authenticate/user',
                    method: 'POST',
                    dataType: 'json',
                    data: {
                        csrfToken: csrf
                    }
                }).done(function (data) {
                    if (data.type !== 'user') {
                        reject('Expected user session from user authentication');
                    } else {
                        localStorage.setItem('api_session', data.session);
                        resolve(data.session);
                    }
                }).fail(function (xhr) {
                    reject(xhr.statusText)
                })
            } else {
                resolve(session);
            }
        } else {
            session = localStorage.getItem('public_api_session');
            if (session === null) {
                $.ajax({
                    url: '/api/v2/authenticate',
                    method: 'POST',
                    dataType: 'json',
                    data: {
                        csrfToken: csrf
                    }
                }).done(function (data) {
                    if (data.type !== 'public') {
                        reject('Expected public session from public authentication')
                    } else {
                        localStorage.setItem('public_api_session', data.session);
                        resolve(data.session);
                    }
                }).fail(function (xhr) {
                    reject(xhr.statusText)
                })
            } else {
                resolve(session);
            }
        }
    });
}

function invalidateApiSession() {
    if (isLoggedIn) {
        localStorage.removeItem('api_session')
    }
    else {
        localStorage.removeItem('public_api_session')
    }
}


//=====> DOCUMENT READY

// Initialize highlighting
hljs.initHighlightingOnLoad();

$(function() {
    $('.alert-fade').fadeIn('slow');

    initTooltips();

    $('.btn-spinner').click(function() {
        const iconClass = $(this).data('icon');
        toggleSpinner($(this).find('[data-fa-i2svg]').toggle(iconClass));
    });

    $(".link-go-back").click(function () {
        window.history.back();
    });
});

// Fix page anchors which were broken by the fixed top navigation

const scrollToAnchor = function (anchor) {
    if (anchor) {
        let target = $("a" + anchor);

        if (target.length) {
            $('html,body').animate({
                scrollTop: target.offset().top - ($("#topbar").height() + 10)
            }, 1);

            return false;
        }
    }

    return true;
};

$(window).load(function () {
    return scrollToAnchor(window.location.hash);
});

$("a[href^='#']").click(function () {
    window.location.replace(window.location.toString().split("#")[0] + $(this).attr("href"));

    return scrollToAnchor(this.hash);
});


//=====> SERVICE WORKER

// The service worker has been removed in commit 9ab90b5f4a5728587fc08176e316edbe88dfce9e.
// This code ensures that the service worker is removed from the browser.

if (window.navigator && navigator.serviceWorker) {
    if ('getRegistrations' in navigator.serviceWorker) {
        navigator.serviceWorker.getRegistrations().then(function (registrations) {
            registrations.forEach(function (registration) {
                registration.unregister();
            })
        })
    } else if ('getRegistration' in navigator.serviceWorker) {
        navigator.serviceWorker.getRegistration().then(function (registration) {
            registration.unregister();
        })
    }
}
