/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Main application file
 *
 * ==================================================
 */

var KEY_ENTER = 13;
var KEY_PLUS = 61;
var KEY_MINUS = 173;

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var CATEGORY_STRING = CATEGORY_STRING || null;
var SORT_STRING = SORT_STRING || null;
var csrf = null;

/*
 * ==================================================
 * =                  Key bindings                  =
 * ==================================================
 */

var KEY_S = 83;                             // Search
var KEY_H = 72;                             // Home
var KEY_C = 67;                             // Create project
var KEY_ESC = 27;                           // De-focus

/*
 * ==================================================
 * =                  Clipboard                     =
 * ==================================================
 */

var clipboardManager = new ClipboardJS('.copy-url');
clipboardManager.on('success', function(e) {
    var element = $('.btn-download').tooltip({title: 'Copied!', placement: 'bottom', trigger: 'manual'}).tooltip('show');
    setTimeout(function () {
        element.tooltip('destroy');
    }, 2200);
});

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function shouldExecuteHotkey(event) {
    return !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey;
}

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

/*
 * ==================================================
 * =               Google Analytics                 =
 * ==================================================
 */
(function(S,p,o,n,g,i,e){S['GoogleAnalyticsObject']=g;S[g]=S[g]||function(){
        (S[g].q=S[g].q||[]).push(arguments)},S[g].l=1*new Date();i=p.createElement(o),
    e=p.getElementsByTagName(o)[0];i.async=1;i.src=n;e.parentNode.insertBefore(i,e)
})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

ga('create', 'UA-59476017-3', 'auto');
ga('send', 'pageview');

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

// Initialize highlighting
hljs.initHighlightingOnLoad();

$(function() {
    $('.alert-fade').fadeIn('slow');

    initTooltips();

    $('.authors-icon').click(function() { window.location = '/authors'; });

    $('.staff-icon').click(function() { window.location = '/staff'; });

    $('.btn-spinner').click(function() {
        var iconClass = $(this).data('icon');
        $(this).find('.' + iconClass).removeClass(iconClass).addClass('fa-spinner fa-spin');
    });

    var searchBar = $('.project-search');
    searchBar.find('input').on('keypress', function(event) {
        if (event.keyCode === KEY_ENTER) {
            event.preventDefault();
            $(this).next().find('.btn').click();
        }
    });

    searchBar.find('.btn').click(function() {
        var query = $(this).closest('.input-group').find('input').val();
        var url = '/?q=' + query;
        if (CATEGORY_STRING) url += '&categories=' + CATEGORY_STRING;
        if (SORT_STRING) url += '&sort=' + SORT_STRING;
        go(url);
    });

    var body = $('body');
    body.keydown(function(event) {
        var target = $(event.target);
        var searchIcon = $('.search-icon');
        if (shouldExecuteHotkey(event)) {
            if (target.is('body')) {
                switch (event.keyCode) {
                    case KEY_S:
                        event.preventDefault();
                        searchIcon.click();
                        break;
                    case KEY_H:
                        event.preventDefault();
                        window.location = '/';
                        break;
                    case KEY_C:
                        event.preventDefault();
                        window.location = '/new';
                        break;
                    case KEY_PLUS:
                        break;
                    case KEY_MINUS:
                        break;
                    default:
                        break;
                }
            } else if (target.is('.project-search input')) {
                switch (event.keyCode) {
                    case KEY_ESC:
                        event.preventDefault();
                        searchIcon.click();
                        break;
                    default:
                        break;
                }
            }
        }
    });

    $(".link-go-back").click(function () {
        window.history.back();
    });
});

// Fix page anchors which were broken by the fixed top navigation

var scrollToAnchor = function (anchor) {
    if (anchor) {
        var target = $("a" + anchor);

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

/*
 * ==================================================
 * =                 Service Worker                 =
 * ==================================================
 *
 * The service worker has been removed in commit 9ab90b5f4a5728587fc08176e316edbe88dfce9e.
 * This code ensures that the service worker is removed from the browser.
 *
 */

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
